package com.example.hdb.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@ConditionalOnProperty(name = "openai.api-key", matchIfMissing = false)
public class OpenAIConfig {
    
    @Value("${openai.api-key}")
    private String openaiApiKey;
    
    @Bean
    public RestTemplate openAiRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        
        // 기본 헤더 설정을 위한 Interceptor
        restTemplate.setInterceptors(java.util.List.of(
            new org.springframework.http.client.ClientHttpRequestInterceptor() {
                @Override
                public org.springframework.http.client.ClientHttpResponse intercept(
                    org.springframework.http.HttpRequest request, 
                    byte[] body, 
                    org.springframework.http.client.ClientHttpRequestExecution execution) throws java.io.IOException {
                    
                    request.getHeaders().add("Authorization", "Bearer " + openaiApiKey);
                    request.getHeaders().add("Content-Type", "application/json");
                    
                    return execution.execute(request, body);
                }
            }
        ));
        
        return restTemplate;
    }
}
