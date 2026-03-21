package com.example.hdb.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "openai.api-key", matchIfMissing = false)
public class OpenAIConfig {
    
    @Value("${openai.api-key}")
    private String openaiApiKey;
    
    // RestTemplate Bean은 RestTemplateConfig.java에서 관리하므로 제거
}
