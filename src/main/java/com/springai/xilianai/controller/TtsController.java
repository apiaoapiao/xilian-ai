package com.springai.xilianai.controller;


import com.springai.xilianai.util.TtsBinaryService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;

@RestController
@RequestMapping("/tts")
@Slf4j
public class TtsController {

    @Resource
    private TtsBinaryService ttsBinaryService;

    /**
     * 语音合成接口：整段返回 wav 二进制（内部转调本地 GPT-SoVITS）。
     *
     * @param request 包含 text 与可选参数
     * @return 二进制音频流
     */
    @PostMapping(value = "/synthesize", produces = "audio/wav")
    public StreamingResponseBody synthesizeAudioBatch(
            @RequestBody TtsRequest request,
            HttpServletResponse response) {

        response.setContentType("audio/wav");
        response.setHeader("Content-Disposition", "inline; filename=\"audio.wav\"");

        return outputStream -> {
            try {
                String text = request.getText();
                log.info("开始批量TTS合成，文本长度: {}", text == null ? 0 : text.length());

                byte[] audioData = ttsBinaryService.synthesizeWav(text, request.getChatId());

                outputStream.write(audioData);
                outputStream.flush();

                log.info("批量TTS合成完成，音频大小: {} bytes", audioData.length);

            } catch (Exception e) {
                log.error("批量TTS合成失败", e);
                throw new RuntimeException("音频生成失败", e);
            } finally {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    log.error("关闭输出流失败", e);
                }
            }
        };
    }

    /**
     * TTS请求参数类
     */
    @Data
    public static class TtsRequest {
        private String text;
        private String chatId;
        private String voiceModel = "default"; // 可选的语音模型
        private Double speed = 1.0; // 语速
    }
}
