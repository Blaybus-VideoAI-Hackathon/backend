package com.example.hdb.util;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JsonUtils {
    
    /**
     * OpenAI 응답에서 JSON 부분만 추출
     * @param response OpenAI 응답 문자열
     * @return JSON 부분만 추출된 문자열
     */
    public static String extractJson(String response) {
        if (response == null || response.trim().isEmpty()) {
            throw new IllegalArgumentException("Response is null or empty");
        }
        
        String trimmed = response.trim();
        
        // 첫 번째 '{' 찾기
        int firstBrace = trimmed.indexOf('{');
        if (firstBrace == -1) {
            log.error("No opening brace found in response: {}", response);
            throw new RuntimeException("No JSON found in response");
        }
        
        // 마지막 '}' 찾기
        int lastBrace = trimmed.lastIndexOf('}');
        if (lastBrace == -1) {
            log.error("No closing brace found in response: {}", response);
            throw new RuntimeException("Invalid JSON format");
        }
        
        // JSON 부분 추출
        String json = trimmed.substring(firstBrace, lastBrace + 1);
        
        log.debug("Extracted JSON: {}", json);
        return json;
    }
    
    /**
     * JSON 배열인 경우 첫 번째 '['부터 마지막 ']'까지 추출
     * @param response OpenAI 응답 문자열
     * @return JSON 배열 부분만 추출된 문자열
     */
    public static String extractJsonArray(String response) {
        if (response == null || response.trim().isEmpty()) {
            throw new IllegalArgumentException("Response is null or empty");
        }
        
        String trimmed = response.trim();
        
        // 첫 번째 '[' 찾기
        int firstBracket = trimmed.indexOf('[');
        if (firstBracket == -1) {
            log.error("No opening bracket found in response: {}", response);
            throw new RuntimeException("No JSON array found in response");
        }
        
        // 마지막 ']' 찾기
        int lastBracket = trimmed.lastIndexOf(']');
        if (lastBracket == -1) {
            log.error("No closing bracket found in response: {}", response);
            throw new RuntimeException("Invalid JSON array format");
        }
        
        // JSON 배열 부분 추출
        String json = trimmed.substring(firstBracket, lastBracket + 1);
        
        log.debug("Extracted JSON array: {}", json);
        return json;
    }
    
    /**
     * 안전한 JSON 추출 (객체 또는 배열)
     * @param response OpenAI 응답 문자열
     * @return JSON 부분만 추출된 문자열
     */
    public static String extractJsonSafely(String response) {
        try {
            return extractJson(response);
        } catch (Exception e) {
            try {
                return extractJsonArray(response);
            } catch (Exception e2) {
                log.error("Failed to extract JSON from response: {}", response, e2);
                throw new RuntimeException("No valid JSON found in response", e2);
            }
        }
    }
}
