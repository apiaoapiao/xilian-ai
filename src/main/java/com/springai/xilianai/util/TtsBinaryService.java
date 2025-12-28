package com.springai.xilianai.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class TtsBinaryService {
    
    private final WebClient webClient;
    private final String apiBaseUrl = "http://127.0.0.1:9880";
    
    public TtsBinaryService() {
        this.webClient = WebClient.builder()
                .baseUrl(apiBaseUrl)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }
    
    /**
     * 合成二进制音频流
     * 
     * @param text 要合成的文本
     * @return 二进制音频数据流
     */
    public Flux<DataBuffer> synthesizeBinary(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Flux.error(new IllegalArgumentException("文本内容不能为空"));
        }
        
        return webClient.post()
                .uri("/tts")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createTtsRequest(text))
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                .doOnNext(buffer -> log.debug("接收到音频数据块，大小: {} bytes", buffer.readableByteCount()));
    }
    
    /**
     * 创建TTS请求体
     */
    private Map<String, Object> createTtsRequest(String text) {
        Map<String, Object> request = new HashMap<>();
        request.put("text", text);
        request.put("text_lang", "zh");
        request.put("ref_audio_path", "F:/GPT-SoVITS-v2pro-20250604/wav/【吃惊】这片麦田可是我们的宝贝，不能随便踩进去哦。会把希望踩坏的。.wav");
        List<String> aux_ref_audio_paths = new ArrayList<>();
        aux_ref_audio_paths.add("F:/GPT-SoVITS-v2pro-20250604/wav/【开心】那时我们都是小孩子呢。.wav");
        aux_ref_audio_paths.add("F:/GPT-SoVITS-v2pro-20250604/wav/【恐惧】就像你的名字那样，背负起最初的混沌，和这个我们深爱的世界吧…….wav");
        aux_ref_audio_paths.add("F:/GPT-SoVITS-v2pro-20250604/wav/【难过】是呀。看着眼前的世界，悲伤的念头还是化作了现实…….wav");
        aux_ref_audio_paths.add("F:/GPT-SoVITS-v2pro-20250604/wav/【其他】（均匀的呼吸声）.wav");
        aux_ref_audio_paths.add("F:/GPT-SoVITS-v2pro-20250604/wav/【生气】嗯，毕竟…你可是我们的憧憬呀。.wav");
        request.put("aux_ref_audio_paths", aux_ref_audio_paths);
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
        request.put("streaming_mode", false);
        request.put("parallel_infer", true);
        request.put("repetition_penalty", 1.35);
        request.put("sample_steps", 32);
        request.put("super_sampling", false);
        request.put("overlap_length", 2);
        request.put("min_chunk_length", 16);
        
        return request;
    }
}