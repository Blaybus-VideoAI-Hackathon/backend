package com.example.hdb.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import jakarta.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.DriverManager;

@Configuration
public class StartupLogger {
    
    private static final Logger logger = LoggerFactory.getLogger(StartupLogger.class);
    
    private final Environment environment;
    
    @Value("${spring.datasource.url:}")
    private String datasourceUrl;
    
    @Value("${spring.datasource.username:}")
    private String datasourceUsername;
    
    @Value("${spring.datasource.password:}")
    private String datasourcePassword;
    
    public StartupLogger(Environment environment) {
        this.environment = environment;
    }
    
    @PostConstruct
    public void logStartupInfo() {
        logger.info("=== Spring Boot Startup Configuration ===");
        
        // Active profiles
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length == 0) {
            activeProfiles = environment.getDefaultProfiles();
        }
        logger.info("Active profiles: {}", String.join(", ", activeProfiles));
        
        // Datasource environment variables check
        boolean urlPresent = !datasourceUrl.isEmpty();
        boolean usernamePresent = !datasourceUsername.isEmpty();
        boolean passwordPresent = !datasourcePassword.isEmpty();
        
        logger.info("Datasource URL present: {}", urlPresent);
        logger.info("Datasource username present: {}", usernamePresent);
        logger.info("Datasource password present: {}", passwordPresent);
        
        // 실제 datasource URL 확인
        if (urlPresent) {
            String maskedUrl = maskDatasourceUrl(datasourceUrl);
            logger.info("Actual datasource URL: {}", maskedUrl);
            
            // H2 사용 여부 확인
            boolean isH2 = datasourceUrl.contains("jdbc:h2:");
            logger.info("Using H2 database: {}", isH2);
            
            // MySQL 사용 여부 확인
            boolean isMySQL = datasourceUrl.contains("jdbc:mysql:");
            logger.info("Using MySQL database: {}", isMySQL);
        }
        
        // JDBC direct connection test
        if (urlPresent && usernamePresent && passwordPresent) {
            testJdbcConnection();
        } else {
            logger.warn("Skipping JDBC connection test - missing datasource configuration");
        }
        
        logger.info("=====================================");
    }
    
    private String maskDatasourceUrl(String url) {
        if (url.contains("password=")) {
            return url.replaceAll("password=[^&]*", "password=***");
        }
        return url;
    }
    
    private void testJdbcConnection() {
        try {
            logger.info("Testing direct JDBC connection...");
            Connection connection = DriverManager.getConnection(
                datasourceUrl, 
                datasourceUsername, 
                datasourcePassword
            );
            
            if (connection != null && !connection.isClosed()) {
                logger.info("JDBC direct connection: SUCCESS");
                connection.close();
            } else {
                logger.error("JDBC direct connection: FAILED - Connection is null or closed");
            }
        } catch (Exception e) {
            logger.error("JDBC direct connection: FAILED - {}", e.getMessage());
            logger.debug("JDBC connection error details:", e);
        }
    }
}
