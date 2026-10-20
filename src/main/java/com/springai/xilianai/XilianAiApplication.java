package com.springai.xilianai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class XilianAiApplication {

    /** 百炼（DashScope）API Key 的环境变量名，兼容历史命名 */
    private static final String[] DASHSCOPE_KEY_ENV_NAMES = {"DASHSCOPE_API_KEY", "alibailian_apikey"};

    public static void main(String[] args) {
        logApiKeyFingerprint("DEEPSEEK_API_KEY", "对话模型");
        SpringApplication application = new SpringApplication(XilianAiApplication.class);
        application.setDefaultProperties(dashScopeDefaultProperties());
        application.run(args);
    }

    /**
     * 打印环境变量中 API Key 的「指纹」（只保留头尾，不泄露完整 key）。
     * 注意：yml 里写的是 api-key: ${DEEPSEEK_API_KEY}，当环境变量缺失时，
     * Spring 会把这个占位符当字面量保留下来（并不为空），
     * 于是启动不会报 "API key must be set"，而是调用时被服务端以 401 拒绝 —— 非常难排查，
     * 所以这里启动时就把有没有拿到 key 打出来。
     */
    private static void logApiKeyFingerprint(String envName, String purpose) {
        String value = System.getenv(envName);
        if (!StringUtils.hasText(value)) {
            log.warn("环境变量 {} 未设置：{} 将回退到 application.yml / application-local.yml 里的配置；"
                    + "若那里也没有配置，调用会返回 401（yml 中的占位符会被当成字面量发送）。", envName, purpose);
            return;
        }
        log.info("环境变量 {} 已就绪（{}），key 指纹：{}", envName, purpose, maskKey(value));
    }

    private static String maskKey(String key) {
        if (key.length() <= 12) {
            return "***（长度 " + key.length() + "）";
        }
        return key.substring(0, 6) + "***" + key.substring(key.length() - 4) + "（长度 " + key.length() + "）";
    }

    /**
     * 对话模型已切换到 DeepSeek（application.yml 中的 spring.ai.deepseek），
     * 而 yml 里的 dashscope 配置已被注释掉。
     * 但知识库向量化仍然依赖百炼的 embedding 模型（DeepSeek 没有 embedding 接口），
     * DashScope 自动配置又强制要求 spring.ai.dashscope.api-key，
     * 因此在这里把环境变量中的百炼 Key 作为「默认属性」补进去：
     * <ul>
     *   <li>yml 里若重新配置了 spring.ai.dashscope.api-key，则以 yml 为准，这里不会覆盖；</li>
     *   <li>没有环境变量时不注入，启动会明确提示缺少 DashScope API Key。</li>
     * </ul>
     */
    private static Map<String, Object> dashScopeDefaultProperties() {
        Map<String, Object> defaults = new HashMap<>();
        for (String envName : DASHSCOPE_KEY_ENV_NAMES) {
            String apiKey = System.getenv(envName);
            if (StringUtils.hasText(apiKey)) {
                defaults.put("spring.ai.dashscope.api-key", apiKey);
                log.info("已从环境变量 {} 注入 DashScope API Key（仅用于知识库 embedding）", envName);
                return defaults;
            }
        }
        log.warn("未找到环境变量 {}，知识库向量化需要百炼 Key，否则启动会因为缺少 DashScope API Key 失败",
                String.join(" / ", DASHSCOPE_KEY_ENV_NAMES));
        return defaults;
    }
}
