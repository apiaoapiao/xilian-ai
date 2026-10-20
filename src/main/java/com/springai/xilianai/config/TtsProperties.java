package com.springai.xilianai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 语音合成（GPT-SoVITS）相关配置，统一在 application.yml 的 tts: 节点下维护。
 *
 * <pre>
 * tts:
 *   base-url: http://127.0.0.1:9880
 *   ref-audio-path: F:/xxx/参考音频.wav
 *   prompt-text: 参考音频对应的文本
 *   aux-ref-audio-paths:
 *     - F:/xxx/情感参考.wav
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "tts")
public class TtsProperties {

    /** GPT-SoVITS 服务地址（改端口只改这里） */
    private String baseUrl = "http://127.0.0.1:9880";

    /** 主参考音频：决定音色 */
    private String refAudioPath = "";

    /** 辅助参考音频：用于情感融合，可留空 */
    private List<String> auxRefAudioPaths = new ArrayList<>();

    /** 参考音频对应的文本，必须与参考音频内容一致 */
    private String promptText = "";

    /** 合成文本的语言 */
    private String textLang = "zh";

    /** 参考音频的语言 */
    private String promptLang = "zh";

    /** 文本切分方式（GPT-SoVITS 的 text_split_method） */
    private String textSplitMethod = "cut3";

    /** 语速 */
    private double speedFactor = 1.0;

    /** 采样温度 */
    private double temperature = 0.8;

    private int topK = 10;

    private double topP = 1.2;

    private double repetitionPenalty = 1.35;

    /** 采样步数，越大越慢越稳 */
    private int sampleSteps = 32;

    /** 单次合成超时 */
    private Duration timeout = Duration.ofSeconds(120);

    /** 前面的合成还在跑时，后一个请求最多排队多久 */
    private Duration queueWait = Duration.ofSeconds(180);

    /** 上下文字数上限，避免 WebClient 缓冲过大 */
    private int maxInMemorySizeMb = 32;
}
