package com.example.hdb.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Leonardo AI API 설정
 * 
 * 환경 변수:
 * - LEONARDO_API_KEY: Leonardo AI API 키
 * - LEONARDO_API_URL: Leonardo API URL (기본값: https://cloud.leonardo.ai/api/rest/v1)
 * - LEONARDO_MODEL_ID: Lucid Origin 모델 ID (기본값: 7b592283-e8a7-4c5a-9ba6-d18c31f258b9)
 */
@Configuration
@Slf4j
public class LeonardoAIConfig {

    @Value("${leonardo.api-key:}")
    private String apiKey;

    @Value("${leonardo.api-url:https://cloud.leonardo.ai/api/rest/v1}")
    private String apiUrl;

    @Value("${leonardo.model-id:7b592283-e8a7-4c5a-9ba6-d18c31f258b9}")
    private String modelId;

    /**
     * Leonardo AI REST 클라이언트
     */
    @Bean(name = "leonardoRestTemplate")
    public RestTemplate leonardoRestTemplate(RestTemplateBuilder builder) {
        log.info("Initializing Leonardo AI RestTemplate");
        log.info("Leonardo API URL: {}", apiUrl);
        log.info("Leonardo Model ID: {}", modelId);

        return builder
                .setConnectTimeout(Duration.ofSeconds(30))
                .setReadTimeout(Duration.ofSeconds(120))
                .build();
    }

    /**
     * Leonardo API 키 getter (Fallback 처리)
     */
    public boolean isLeonardoConfigured() {
        boolean configured = apiKey != null && !apiKey.isBlank();
        if (!configured) {
            log.warn("Leonardo AI API Key is not configured");
        }
        return configured;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public String getModelId() {
        return modelId;
    }
}
