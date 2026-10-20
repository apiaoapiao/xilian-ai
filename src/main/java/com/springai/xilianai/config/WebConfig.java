package com.springai.xilianai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
               // 浏览器对 POST 一定会带 Origin（GET 不带），所以必须包含前端 dev server 的来源，
               // 否则 TTS 这类 POST 接口会被 Spring 直接拒绝为 403 "Invalid CORS request"。
               // 用 allowedOriginPatterns 支持任意本机端口（5431/5432/5173…）。
               .allowedOriginPatterns("http://localhost:[*]", "http://127.0.0.1:[*]")
               .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
               .allowedHeaders("*")
               .exposedHeaders("*")
               .allowCredentials(true)
               .maxAge(3600);
    }
}
