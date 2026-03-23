package com.example.hdb.service;

import com.example.hdb.config.KlingConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class KlingTokenService {
    
    private final KlingConfig klingConfig;
    private final RestTemplate restTemplate;
    
    // 토큰 캐싱 (간단한 메모리 캐시)
    private final ConcurrentHashMap<String, TokenInfo> tokenCache = new ConcurrentHashMap<>();
    
    /**
     * Kling API용 Bearer 토큰 생성/조회
     */
    public String getBearerToken() {
        String cacheKey = "kling_token";
        TokenInfo cached = tokenCache.get(cacheKey);
        
        // 토큰이 있고 유효기간이 지나지 않았으면 재사용
        if (cached != null && cached.getExpiryTime().isAfter(LocalDateTime.now().plusMinutes(5))) {
            log.debug("Using cached Kling token");
            return "Bearer " + cached.getToken();
        }
        
        // 새 토큰 발급
        return generateNewToken();
    }
    
    /**
     * 새로운 토큰 발급
     */
    private String generateNewToken() {
        log.info("Generating new Kling token");
        
        try {
            // Kling AI는 별도 토큰 발급 없이 API Key를 Bearer 토큰으로 직접 사용
            // 참고: https://aimlapi.com/generate-video-with-kling-ai-api
            String apiKey = klingConfig.getAccessKey();
            
            if (apiKey == null || apiKey.trim().isEmpty()) {
                log.error("Kling API key is null or empty");
                throw new RuntimeException("Kling API key not configured");
            }
            
            log.info("Using Kling API key: {}", apiKey.substring(0, Math.min(10, apiKey.length())) + "...");
            
            // 토큰 캐싱 (1시간 유효)
            TokenInfo tokenInfo = new TokenInfo(apiKey, LocalDateTime.now().plusHours(1));
            tokenCache.put("kling_token", tokenInfo);
            
            log.info("Kling token generated successfully (using API key as Bearer token)");
            return "Bearer " + apiKey;
            
        } catch (Exception e) {
            log.error("Error generating Kling token", e);
            log.error("Kling config - baseUrl: {}, accessKey: {}", 
                    klingConfig.getBaseUrl(), 
                    klingConfig.getAccessKey() != null ? klingConfig.getAccessKey().substring(0, Math.min(5, klingConfig.getAccessKey().length())) + "..." : "null");
            throw new RuntimeException("Failed to generate Kling token", e);
        }
    }
    
    /**
     * 응답에서 토큰 추출 (실제 API 스펙에 맞게 수정 필요)
     */
    private String extractTokenFromResponse(String responseBody) {
        // TODO: 실제 Kling API 응답 형식에 맞게 파싱 로직 구현
        // 임시로 간단한 파싱 - 실제로는 Jackson 등 사용
        if (responseBody.contains("\"token\"")) {
            int start = responseBody.indexOf("\"token\":\"") + 9;
            int end = responseBody.indexOf("\"", start);
            return responseBody.substring(start, end);
        }
        throw new RuntimeException("Token not found in response");
    }
    
    /**
     * 토큰 정보 캐시용 내부 클래스
     */
    private static class TokenInfo {
        private final String token;
        private final LocalDateTime expiryTime;
        
        public TokenInfo(String token, LocalDateTime expiryTime) {
            this.token = token;
            this.expiryTime = expiryTime;
        }
        
        public String getToken() {
            return token;
        }
        
        public LocalDateTime getExpiryTime() {
            return expiryTime;
        }
    }
}
