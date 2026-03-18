package com.example.hdb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@SpringBootApplication
@EnableJpaRepositories
public class HdbApplication {
    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(HdbApplication.class, args);
        
        // DataSource 확인 코드 (임시)
        try {
            DataSource dataSource = context.getBean(DataSource.class);
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            
            System.out.println("=== DataSource 확인 ===");
            System.out.println("DataSource URL: " + dataSource.getConnection().getMetaData().getURL());
            System.out.println("DataSource Username: " + dataSource.getConnection().getMetaData().getUserName());
            System.out.println("Database Product: " + dataSource.getConnection().getMetaData().getDatabaseProductName());
            System.out.println("Database Name: " + dataSource.getConnection().getCatalog());
            System.out.println("========================");
            
            // 현재 데이터베이스의 테이블 목록 확인
            jdbcTemplate.query("SHOW TABLES", (rs) -> {
                System.out.println("Table: " + rs.getString(1));
                return null;
            });
            
        } catch (Exception e) {
            System.err.println("DataSource 확인 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
