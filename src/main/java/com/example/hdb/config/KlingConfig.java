package com.example.hdb.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "kling")
@Data
public class KlingConfig {
    
    private String accessKey;
    private String secretKey;
    private String baseUrl;
}
