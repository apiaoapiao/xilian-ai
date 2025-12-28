package com.springai.xilianai.controller;

import com.springai.xilianai.app.Client;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@Slf4j
@RestController
@RequestMapping("/ai")
public class AiController {
    @Resource
    private Client client;

    /**
     * 流式对话：返回文本和音频URL，音频通过独立服务获取
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatStream(
            @RequestParam String message,
            @RequestParam(required = false, defaultValue = "default") String chatId) {

        return client.collectFullResponse(message, chatId);
    }
}
