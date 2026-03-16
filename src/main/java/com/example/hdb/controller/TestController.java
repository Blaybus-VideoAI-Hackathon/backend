package com.example.hdb.controller;

import com.example.hdb.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
@Slf4j
public class TestController {

    private final ProjectRepository projectRepository;

    @GetMapping("/db")
    public ResponseEntity<Map<String, Object>> testDatabaseConnection() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Repository 테스트
            long projectCount = projectRepository.count();
            
            response.put("status", "database connected");
            response.put("projectCount", projectCount);
            response.put("message", "Database connection successful");
            
            log.info("Database connection test successful. Project count: {}", projectCount);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Database connection test failed", e);
            
            response.put("status", "database connection failed");
            response.put("error", e.getMessage());
            response.put("message", "Database connection failed");
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "healthy");
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }
}
