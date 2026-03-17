package com.example.hdb.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

@Configuration
public class DataSourceLogger {
    
    private static final Logger logger = LoggerFactory.getLogger(DataSourceLogger.class);
    
    @Value("${spring.datasource.url:}")
    private String datasourceUrl;
    
    @Value("${spring.datasource.username:}")
    private String datasourceUsername;
    
    @PostConstruct
    public void logDataSourceConfig() {
        logger.info("=== DataSource Configuration Check ===");
        logger.info("SPRING_DATASOURCE_URL present: {}", !datasourceUrl.isEmpty());
        logger.info("SPRING_DATASOURCE_USERNAME present: {}", !datasourceUsername.isEmpty());
        
        if (!datasourceUrl.isEmpty()) {
            // URL에서 민감 정보 제거하고 로그
            String maskedUrl = datasourceUrl.replaceAll("password=[^&]*", "password=***");
            logger.info("Datasource URL (masked): {}", maskedUrl);
        }
        
        if (!datasourceUsername.isEmpty()) {
            logger.info("Datasource Username: {}", datasourceUsername);
        }
        
        logger.info("===================================");
    }
}
