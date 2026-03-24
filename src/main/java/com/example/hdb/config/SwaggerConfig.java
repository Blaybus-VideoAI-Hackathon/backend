package com.example.hdb.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        SecurityScheme securityScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .name("BearerAuth");

        SecurityRequirement securityRequirement = new SecurityRequirement()
                .addList("BearerAuth");

        return new OpenAPI()
                .info(new Info()
                        .title("HDB Backend API")
                        .description("AI 영상 생성 서비스 백엔드 API")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("HDB Team")
                                .email("hdb@example.com")))
                .addSecurityItem(securityRequirement)
                .components(new Components()
                        .addSecuritySchemes("BearerAuth", securityScheme));
    }
}