package com.springai.xilianai.controller;


import com.springai.xilianai.util.TtsBinaryService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@RestController
@RequestMapping("/tts")
@Slf4j
public class TtsController {
    
    @Resource
    private TtsBinaryService ttsBinaryService;
    
    /**
     * 独立的TTS服务接口，返回二进制音频流
     * 
     * @param request 包含text和可选参数
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
                log.info("开始批量TTS合成，文本长度: {}", request.getText().length());

                // 收集所有音频数据
                byte[] audioData = ttsBinaryService.synthesizeBinary(request.getText())
                        .collectList()
                        .map(dataBuffers -> {
                            // 合并所有DataBuffer
                            int totalSize = dataBuffers.stream()
                                    .mapToInt(DataBuffer::readableByteCount)
                                    .sum();

                            ByteArrayOutputStream baos = new ByteArrayOutputStream(totalSize);
                            for (DataBuffer buffer : dataBuffers) {
                                byte[] bytes = new byte[buffer.readableByteCount()];
                                buffer.read(bytes);
                                baos.write(bytes, 0, bytes.length);
                                DataBufferUtils.release(buffer);
                            }
                            return baos.toByteArray();
                        })
                        .block();  // 在非反应式环境中阻塞获取

                // 写入响应
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
        private String voiceModel = "default"; // 可选的语音模型
        private Double speed = 1.0; // 语速
    }
}