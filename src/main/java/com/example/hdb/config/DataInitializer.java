package com.example.hdb.config;

import com.example.hdb.entity.User;
import com.example.hdb.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataInitializer {

    private final UserRepository userRepository;

    @Bean
    public CommandLineRunner initData() {
        return args -> {
            log.info("=== 초기 사용자 데이터 생성 시작 ===");
            
            String[] usernames = {"user1", "user2", "user3", "user4", "user5"};
            String password = "1111";
            
            for (String username : usernames) {
                if (!userRepository.existsByLoginId(username)) {
                    User user = User.builder()
                            .loginId(username)
                            .password(password) // 평문 저장
                            .name(username.toUpperCase())
                            .role(User.UserRole.USER)
                            .createdAt(LocalDateTime.now()) // Fallback: 수동으로 createdAt 설정
                            .build();
                    
                    userRepository.save(user);
                    log.info("사용자 생성 완료: {}", username);
                } else {
                    log.info("사용자 이미 존재: {}", username);
                }
            }
            
            log.info("=== 초기 사용자 데이터 생성 완료 ===");
            log.info("총 사용자 수: {}", userRepository.count());
        };
    }
}
