package com.springai.xilianai.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class StreamingTtsService {
    
    private final WebClient webClient;
    private final SentenceSegmenter segmenter;

    private final String apiBaseUrl="http://127.0.0.1:9880";
    
    public StreamingTtsService(SentenceSegmenter segmenter) {
        this.segmenter = segmenter;
        this.webClient = WebClient.builder()
            .baseUrl(apiBaseUrl)
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
            .build();
    }

    /**
     * 流式TTS合成 - 整体合成版本
     */
    public Flux<String> synthesizeStreaming(String text) {
        log.debug("开始整体TTS合成，文本长度: {}", text.length());

        return webClient.post()
                .uri("/tts")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createTtsRequest(text))
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return Base64.getEncoder().encodeToString(bytes);
                })
                .map(base64 -> "AUDIO_CHUNK:" + base64)
                .doOnSubscribe(s -> log.debug("开始接收TTS音频流"))
                .doOnComplete(() -> log.debug("TTS音频流接收完成"))
                .onErrorResume(e -> {
                    log.error("TTS整体合成失败", e);
                    return Flux.just("ERROR:TTS合成失败 - " + e.getMessage());
                });
    }
    
    /**
     * 创建TTS请求体（使用你的参数）
     */
    private Map<String, Object> createTtsRequest(String text) {
        Map<String, Object> request = new HashMap<>();
        request.put("text", text);
        request.put("text_lang", "zh");
        request.put("ref_audio_path", "F:/GPT-SoVITS-v2pro-20250604/wav/xilian.wav");
        request.put("prompt_lang", "zh");
        request.put("prompt_text", "这片麦田可是我们的宝贝，不能随便踩进去哦。会把希望踩坏的。");
        request.put("top_k", 5);
        request.put("top_p", 1);
        request.put("temperature", 1);
        request.put("text_split_method", "cut5");
        request.put("batch_size", 1);
        request.put("batch_threshold", 0.75);
        request.put("split_bucket", true);
        request.put("speed_factor", 1);
        request.put("fragment_interval", 0.3);
        request.put("seed", -1);
        request.put("media_type", "wav");
        request.put("streaming_mode", false);  // 关键：启用流式
        request.put("parallel_infer", true);
        request.put("repetition_penalty", 1.35);
        request.put("sample_steps", 32);
        request.put("super_sampling", false);
        request.put("overlap_length", 2);
        request.put("min_chunk_length", 16);
        return request;
    }
    
    /**
     * 分段合成：将长文本分割后分别合成
     */
    public Flux<String> synthesizeSegmented(String longText) {
        List<String> sentences = segmenter.segmentIntoSentences(longText);
        
        return Flux.fromIterable(sentences)
            .concatMap(sentence -> {
                // 对于每个句子，返回一个Flux
                return Flux.concat(
                    Mono.just("TEXT_SEGMENT:" + sentence),
                    synthesizeStreaming(sentence)
                );
            })
            .concatWith(Mono.just("AUDIO_END")); // 音频结束标记
    }
}