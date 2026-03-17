package com.example.hdb.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("HDB Backend API")
                        .description("AI 영상 생성 서비스 백엔드 API")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("HDB Team")
                                .email("hdb@example.com")));
        // servers 설정 제거 - 현재 접속 호스트를 자동으로 사용
    }
}
