package com.example.hdb.config;

import com.example.hdb.entity.User;
import com.example.hdb.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataInitializer {

    private final UserRepository userRepository;

    @Bean
    public CommandLineRunner initData() {
        return args -> {
            log.info("Initializing default users...");
            
            // BCryptPasswordEncoder 생성
            BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
            
            // 초기 계정 5개 생성
            createDefaultUserIfNotExists("user1", "1111", "사용자1", passwordEncoder);
            createDefaultUserIfNotExists("user2", "1111", "사용자2", passwordEncoder);
            createDefaultUserIfNotExists("user3", "1111", "사용자3", passwordEncoder);
            createDefaultUserIfNotExists("user4", "1111", "사용자4", passwordEncoder);
            createDefaultUserIfNotExists("user5", "1111", "사용자5", passwordEncoder);
            
            log.info("Default users initialization completed.");
        };
    }
    
    private void createDefaultUserIfNotExists(String loginId, String password, String name, BCryptPasswordEncoder passwordEncoder) {
        if (!userRepository.existsByLoginId(loginId)) {
            User user = User.builder()
                    .loginId(loginId)
                    .password(passwordEncoder.encode(password)) // BCrypt로 비밀번호 인코딩
                    .name(name)
                    .role(User.UserRole.USER)
                    .createdAt(LocalDateTime.now())
                    .build();
            
            userRepository.save(user);
            log.info("Created default user: {} ({})", loginId, name);
        } else {
            log.info("User {} already exists, skipping creation.", loginId);
        }
    }
}
