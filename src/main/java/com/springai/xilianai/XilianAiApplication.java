package com.springai.xilianai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class XilianAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(XilianAiApplication.class, args);
    }

}
