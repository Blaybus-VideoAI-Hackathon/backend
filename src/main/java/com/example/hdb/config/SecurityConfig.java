package com.example.hdb.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                // Swagger 완전 허용
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/v3/api-docs/**").permitAll()
                // 테스트 엔드포인트 허용
                .requestMatchers("/api/test/**").permitAll()
                // 인증 관련 API 전체 허용
                .requestMatchers("/api/auth/**").permitAll()
                // 나머지는 인증 필요
                .anyRequest().authenticated()
            )
            // 기본 로그인 페이지 완전 비활성화
            .formLogin(AbstractHttpConfigurer::disable)
            // HTTP Basic 완전 비활성화
            .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
