package com.example.hdb.service;

import com.example.hdb.config.KlingConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class KlingTokenService {

    private final KlingConfig klingConfig;

    // 토큰 캐싱 (메모리 캐시)
    private final ConcurrentHashMap<String, TokenInfo> tokenCache = new ConcurrentHashMap<>();

    /**
     * Kling 공식 API용 JWT Bearer 토큰 반환
     * 캐시된 토큰이 유효하면 재사용, 만료 5분 전이면 새로 발급
     */
    public String getBearerToken() {
        String cacheKey = "kling_token";
        TokenInfo cached = tokenCache.get(cacheKey);

        if (cached != null && cached.getExpiryTime().isAfter(LocalDateTime.now().plusMinutes(5))) {
            log.debug("Using cached Kling JWT token");
            return "Bearer " + cached.getToken();
        }

        return generateJwtToken();
    }

    /**
     * Kling 공식 JWT 토큰 생성 (순수 Java, 외부 라이브러리 없음)
     * 참고: https://docs.qingque.cn/d/home/eZQDpPqPlNEIPRSJ8e9Hgb9Jn
     *
     * Header: {"alg":"HS256","typ":"JWT"}
     * Payload: {"iss":"accessKey","exp":now+1800,"nbf":now-5}
     */
    private String generateJwtToken() {
        log.info("=== Generating new Kling JWT token ===");

        String accessKey = klingConfig.getAccessKey();
        String secretKey = klingConfig.getSecretKey();

        if (accessKey == null || accessKey.trim().isEmpty()) {
            log.error("Kling accessKey is null or empty");
            throw new RuntimeException("Kling accessKey not configured");
        }
        if (secretKey == null || secretKey.trim().isEmpty()) {
            log.error("Kling secretKey is null or empty");
            throw new RuntimeException("Kling secretKey not configured");
        }

        log.info("Kling accessKey prefix: {}...", accessKey.substring(0, Math.min(8, accessKey.length())));

        try {
            long nowSeconds = System.currentTimeMillis() / 1000;
            long exp = nowSeconds + 1800; // 30분 후 만료
            long nbf = nowSeconds - 5;   // 5초 전부터 유효 (시간 오차 허용)

            // 1. Header (Base64URL 인코딩)
            String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
            String encodedHeader = base64UrlEncode(headerJson.getBytes(StandardCharsets.UTF_8));

            // 2. Payload (Base64URL 인코딩)
            String payloadJson = String.format(
                    "{\"iss\":\"%s\",\"exp\":%d,\"nbf\":%d}",
                    accessKey, exp, nbf
            );
            String encodedPayload = base64UrlEncode(payloadJson.getBytes(StandardCharsets.UTF_8));

            // 3. Signing Input
            String signingInput = encodedHeader + "." + encodedPayload;

            // 4. HMAC-SHA256 서명
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec signingKeySpec = new SecretKeySpec(
                    secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(signingKeySpec);
            byte[] signatureBytes = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
            String encodedSignature = base64UrlEncode(signatureBytes);

            // 5. JWT 조합
            String jwt = signingInput + "." + encodedSignature;

            // 캐싱 (25분 유효 - 만료 5분 전에 자동 갱신)
            TokenInfo tokenInfo = new TokenInfo(jwt, LocalDateTime.now().plusMinutes(25));
            tokenCache.put("kling_token", tokenInfo);

            log.info("Kling JWT token generated successfully");
            return "Bearer " + jwt;

        } catch (Exception e) {
            log.error("Failed to generate Kling JWT token", e);
            throw new RuntimeException("Failed to generate Kling JWT token", e);
        }
    }

    /**
     * Base64URL 인코딩 (패딩 제거)
     */
    private String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(data);
    }

    // ──────────────────────────────────────────
    // 내부 캐시 클래스
    // ──────────────────────────────────────────

    private static class TokenInfo {
        private final String token;
        private final LocalDateTime expiryTime;

        public TokenInfo(String token, LocalDateTime expiryTime) {
            this.token = token;
            this.expiryTime = expiryTime;
        }

        public String getToken() { return token; }
        public LocalDateTime getExpiryTime() { return expiryTime; }
    }
}