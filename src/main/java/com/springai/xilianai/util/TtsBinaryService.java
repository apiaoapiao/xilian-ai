package com.springai.xilianai.util;

import com.springai.xilianai.config.TtsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 语音合成：把文本转给本地 GPT-SoVITS，拿到 wav 字节。
 * 服务地址、参考音频、超时等全部来自 application.yml 的 tts: 配置（见 {@link TtsProperties}）。
 */
@Service
@Slf4j
public class TtsBinaryService {

    private final TtsProperties ttsProperties;
    private final WebClient webClient;

    /**
     * GPT-SoVITS 是单卡推理，并发合成只会让显存/耗时翻倍，
     * 这里用公平信号量保证同一时刻只有一个合成在执行（多标签页也拦得住）。
     */
    private final Semaphore synthesisLock = new Semaphore(1, true);

    public TtsBinaryService(TtsProperties ttsProperties) {
        this.ttsProperties = ttsProperties;
        this.webClient = WebClient.builder()
                .baseUrl(ttsProperties.getBaseUrl())
                .codecs(configurer -> configurer.defaultCodecs()
                        .maxInMemorySize(ttsProperties.getMaxInMemorySizeMb() * 1024 * 1024))
                .build();
        log.info("TTS 服务地址: {}，参考音频: {}", ttsProperties.getBaseUrl(), ttsProperties.getRefAudioPath());
    }

    /**
     * 合成整段语音，返回「头部长度已修正」的 wav 字节。
     *
     * <p>注意：只向 GPT-SoVITS 发一次请求。旧实现先把同一个 Flux 交给落盘逻辑订阅一次，
     * 又把 Flux 返回给 Controller 再订阅一次，同一句话会被合成两遍（耗时翻倍、额度/显存白耗）。</p>
     */
    public byte[] synthesizeWav(String text, String chatId) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("文本内容不能为空");
        }
        boolean acquired = false;
        try {
            acquired = synthesisLock.tryAcquire(ttsProperties.getQueueWait().toSeconds(), TimeUnit.SECONDS);
            if (!acquired) {
                throw new IllegalStateException("语音合成队列繁忙，请稍后再试");
            }
            byte[] audio = downloadAudio(text);
            byte[] wav = fixWavHeader(audio);
            saveAudioFile(wav, chatId);
            return wav;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("语音合成被中断", e);
        } finally {
            if (acquired) {
                synthesisLock.release();
            }
        }
    }

    /** 拉取音频字节（一次 HTTP 请求，内部收集完成） */
    private byte[] downloadAudio(String text) {
        List<DataBuffer> buffers = webClient.post()
                .uri("/tts")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createTtsRequest(text))
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                .doOnNext(buffer -> log.debug("接收到音频数据块，大小: {} bytes", buffer.readableByteCount()))
                .collectList()
                .block(ttsProperties.getTimeout());

        if (buffers == null || buffers.isEmpty()) {
            throw new IllegalStateException(
                    "TTS 服务没有返回音频数据，请确认 GPT-SoVITS 已在 " + ttsProperties.getBaseUrl() + " 启动");
        }

        int totalSize = buffers.stream().mapToInt(DataBuffer::readableByteCount).sum();
        byte[] audioBytes = new byte[totalSize];
        int offset = 0;
        for (DataBuffer buffer : buffers) {
            try {
                int count = buffer.readableByteCount();
                buffer.read(audioBytes, offset, count);
                offset += count;
            } finally {
                DataBufferUtils.release(buffer);
            }
        }
        return audioBytes;
    }

    /**
     * GPT-SoVITS 在 streaming_mode=true 时会把 wav 的 RIFF/data 长度字段写成 0，
     * 浏览器拿到这种文件经常「没有声音」。这里按块结构把两个长度补成真实值。
     */
    private static byte[] fixWavHeader(byte[] wav) {
        if (wav == null || wav.length < 44) {
            return wav;
        }
        boolean isRiff = wav[0] == 'R' && wav[1] == 'I' && wav[2] == 'F' && wav[3] == 'F'
                && wav[8] == 'W' && wav[9] == 'A' && wav[10] == 'V' && wav[11] == 'E';
        if (!isRiff) {
            return wav; // 不是 wav（例如错误 JSON），原样返回
        }

        int pos = 12;
        while (pos + 8 <= wav.length) {
            String chunkId = new String(wav, pos, 4, StandardCharsets.ISO_8859_1);
            long chunkSize = readUInt32LE(wav, pos + 4);
            if ("data".equals(chunkId)) {
                int actualSize = wav.length - (pos + 8);
                if (chunkSize != actualSize) {
                    writeUInt32LE(wav, pos + 4, actualSize);
                    log.debug("已修正 wav data 块长度: {} -> {}", chunkSize, actualSize);
                }
                writeUInt32LE(wav, 4, wav.length - 8); // RIFF 块长度 = 文件长度 - 8
                return wav;
            }
            long next = pos + 8 + chunkSize + (chunkSize % 2);
            if (next <= pos || next > wav.length) {
                break;
            }
            pos = (int) next;
        }
        log.warn("未能定位 wav 的 data 块，跳过长度修正（共 {} 字节）", wav.length);
        return wav;
    }

    private static long readUInt32LE(byte[] data, int offset) {
        return (data[offset] & 0xFFL)
                | (data[offset + 1] & 0xFFL) << 8
                | (data[offset + 2] & 0xFFL) << 16
                | (data[offset + 3] & 0xFFL) << 24;
    }

    private static void writeUInt32LE(byte[] data, int offset, long value) {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) ((value >> 8) & 0xFF);
        data[offset + 2] = (byte) ((value >> 16) & 0xFF);
        data[offset + 3] = (byte) ((value >> 24) & 0xFF);
    }

    /** 把合成结果另存一份到 tmp/audio-data/audio-data_<chatId>/ */
    private void saveAudioFile(byte[] audio, String chatId) {
        Path dirPath = Paths.get(System.getProperty("user.dir"), "tmp", "audio-data", "audio-data_" + sanitize(chatId));
        Path filePath = dirPath.resolve("audio_" + System.currentTimeMillis() + ".wav");
        try {
            Files.createDirectories(dirPath);
            Files.write(filePath, audio, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            log.debug("音频已保存：{}", filePath);
        } catch (IOException e) {
            // 落盘只是留档，失败不影响接口返回
            log.error("保存音频失败：{}", filePath, e);
        }
    }

    /** chatId 会作为目录名，去掉非法字符，避免路径穿越 */
    private static String sanitize(String chatId) {
        if (chatId == null || chatId.isBlank()) {
            return "default";
        }
        return chatId.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    /**
     * 创建 TTS 请求体。参考音频、语言等来自 yml 的 tts 配置；
     * streaming_mode 必须是 false：true 时 GPT-SoVITS 返回的是带占位长度的流式 wav，
     * 这里的接口本身是「整段返回」，用不到流式。
     */
    private Map<String, Object> createTtsRequest(String text) {
        Map<String, Object> request = new HashMap<>();
        request.put("text", text);
        request.put("text_lang", ttsProperties.getTextLang());
        request.put("ref_audio_path", ttsProperties.getRefAudioPath());
        List<String> auxRefAudioPaths = ttsProperties.getAuxRefAudioPaths();
        if (auxRefAudioPaths != null && !auxRefAudioPaths.isEmpty()) {
            request.put("aux_ref_audio_paths", auxRefAudioPaths);
        }
        request.put("prompt_lang", ttsProperties.getPromptLang());
        request.put("prompt_text", ttsProperties.getPromptText());
        request.put("text_split_method", ttsProperties.getTextSplitMethod());
        request.put("speed_factor", ttsProperties.getSpeedFactor());
        request.put("temperature", ttsProperties.getTemperature());
        request.put("top_k", ttsProperties.getTopK());
        request.put("top_p", ttsProperties.getTopP());
        request.put("repetition_penalty", ttsProperties.getRepetitionPenalty());
        request.put("sample_steps", ttsProperties.getSampleSteps());
        // 以下为固定参数
        request.put("batch_size", 1);
        request.put("batch_threshold", 0.75);
        request.put("split_bucket", true);
        request.put("fragment_interval", 0.3);
        request.put("seed", -1);
        request.put("media_type", "wav");
        request.put("streaming_mode", false);
        request.put("parallel_infer", true);
        request.put("super_sampling", false);
        request.put("overlap_length", 2);
        request.put("min_chunk_length", 16);
        return request;
    }
}
