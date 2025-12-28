package com.springai.xilianai.controller;

import com.springai.xilianai.chatmemory.FileBasedChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/text")
public class TextController {

    @GetMapping("/memory")
    public List<Message> gettext(@RequestParam(required = false, defaultValue = "default") String chatId) {
        String fileDir = System.getProperty("user.dir") + "/tmp/chat-memory";
        return new FileBasedChatMemory(fileDir).get(chatId);
    }

    @GetMapping("/filename")
    public List<String> gettextname(){
        String fileDir = System.getProperty("user.dir") + "/tmp/chat-memory";
        return new FileBasedChatMemory(fileDir).findKryoFiles(fileDir);
    }
}
