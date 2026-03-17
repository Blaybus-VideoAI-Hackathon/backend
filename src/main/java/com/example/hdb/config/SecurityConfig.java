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
                // Swagger 허용
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                // 테스트 엔드포인트 허용
                .requestMatchers("/api/test/**").permitAll()
                // 로그인 API 허용
                .requestMatchers("/api/auth/login").permitAll()
                // 나머지는 인증 필요
                .anyRequest().authenticated()
            )
            // 기본 로그인 페이지 비활성화
            .formLogin(AbstractHttpConfigurer::disable)
            // HTTP Basic 비활성화
            .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
