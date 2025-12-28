package com.springai.xilianai.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class TtsTool {


    private final RestTemplate restTemplate = new RestTemplate();
    //调用tts的端口
    private final String apiBaseUrl="http://127.0.0.1:9880";
    //需要仓靠音频路径
    private final String defaultRefAudioPath="";

    private final String defaultTextLang="zh";

    private final String defaultPromptLang="zh";

    /**
     * 文本转语音 - 使用默认参数
     */
    public String textToSpeech(String text) {
        return textToSpeech(text, defaultTextLang, defaultRefAudioPath, defaultPromptLang);
    }

    /**
     * 文本转语音 - 完整参数版本
     */
    @Tool(description = "将文本转换为语音。当用户请求语音回复、或需要听一段文字时使用此工具。")
    public String textToSpeech(
            @ToolParam(description = "需要转换为语音的文本内容") String text,
            @ToolParam(description = "文本的语言，例如：zh（中文）、en（英文）") String textLang,
            @ToolParam(description = "参考音频文件的本地路径，用于指定声音特征") String refAudioPath,
            @ToolParam(description = "参考音频的语言") String promptLang) {

        log.info("开始TTS转换，文本长度: {} 字符", text.length());

        try {
            // 1. 调用GPT-SOVITS API
            byte[] audioBytes = callGptSovitsApi(text, textLang, refAudioPath, promptLang);

            // 2. 将音频字节数组转换为Base64编码的Data URL
            String base64Audio = Base64.getEncoder().encodeToString(audioBytes);
            String audioDataUrl = "data:audio/wav;base64," + base64Audio;

            log.info("TTS转换成功，音频数据大小: {} bytes", audioBytes.length);
            return audioDataUrl;

        } catch (Exception e) {
            log.error("TTS转换失败: {}", e.getMessage(), e);
            // 返回错误信息，也可以选择抛出自定义异常
            return "语音合成失败: " + e.getMessage();
        }
    }

    /**
     * 调用GPT-SOVITS API的核心方法
     */
    private byte[] callGptSovitsApi(String text, String textLang, String refAudioPath, String promptLang) {
        String apiUrl = apiBaseUrl + "/tts";

        // 1. 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("User-Agent", "SpringAI-TTS-Client/1.0");

        // 2. 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("text", text);
        requestBody.put("text_lang", textLang);
        requestBody.put("ref_audio_path", refAudioPath);
        requestBody.put("prompt_lang", promptLang);
        requestBody.put("prompt_text", "");
        requestBody.put("top_k", 5);
        requestBody.put("top_p", 1.0);
        requestBody.put("temperature", 1.0);
        requestBody.put("speed_factor", 1.0);

        // 3. 处理长文本（GPT-SOVITS可能有长度限制）
        if (text.length() > 500) {
            log.warn("文本长度超过500字符，可能会影响合成效果，建议分段处理");
            // 如果需要，这里可以添加文本分段逻辑
        }

        // 4. 发送请求
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            log.debug("调用GPT-SOVITS API: {}", apiUrl);
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    entity,
                    byte[].class
            );

            // 5. 检查响应
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            } else {
                throw new RuntimeException("TTS API返回错误状态: " + response.getStatusCode());
            }

        } catch (HttpClientErrorException e) {
            throw new RuntimeException("TTS API调用失败: " + e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new RuntimeException("TTS API调用异常: " + e.getMessage(), e);
        }
    }
}