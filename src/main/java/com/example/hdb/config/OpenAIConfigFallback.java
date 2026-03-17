package com.example.hdb.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class OpenAIConfigFallback {
    
    @Bean
    @ConditionalOnMissingBean(name = "openAiRestTemplate")
    public RestTemplate fallbackRestTemplate() {
        // OpenAI API Key가 없을 때 사용할 기본 RestTemplate
        return new RestTemplate();
    }
}
