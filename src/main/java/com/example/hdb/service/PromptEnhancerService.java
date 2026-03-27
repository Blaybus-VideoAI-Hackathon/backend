package com.example.hdb.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * 이미지 프롬프트 향상 서비스
 *
 * 한국어 imagePrompt를 Leonardo AI에 최적화된 영어 프롬프트로 변환합니다.
 * 1단계: 한국어 → 영어 번역
 * 2단계: 프로젝트 style + 품질 키워드 보강
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PromptEnhancerService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${openai.api-key:}")
    private String openaiApiKey;

    /**
     * 한국어 프롬프트를 영어로 번역 + Leonardo AI에 최적화된 형태로 보강
     *
     * @param koreanPrompt 한국어 이미지 프롬프트
     * @param style        프로젝트 스타일 (예: "cartoon", "realistic", "anime" 등)
     * @param ratio        프로젝트 비율 (예: "16:9", "1:1")
     * @return 영어로 번역+보강된 프롬프트
     */
    public String enhancePrompt(String koreanPrompt, String style, String ratio) {
        log.info("=== Prompt Enhancement Started ===");
        log.info("Original Korean prompt: {}", koreanPrompt);
        log.info("Project style: {}, ratio: {}", style, ratio);

        if (openaiApiKey == null || openaiApiKey.isBlank() || openaiApiKey.equals("sk-test-key-for-development")) {
            log.warn("OpenAI API key not configured, using basic translation fallback");
            return buildFallbackPrompt(koreanPrompt, style);
        }

        try {
            String enhanced = callOpenAIForEnhancement(koreanPrompt, style, ratio);
            log.info("Enhanced prompt: {}", enhanced);
            return enhanced;
        } catch (Exception e) {
            log.error("Prompt enhancement failed, using fallback", e);
            return buildFallbackPrompt(koreanPrompt, style);
        }
    }

    /**
     * OpenAI API를 호출하여 프롬프트를 번역+보강
     */
    private String callOpenAIForEnhancement(String koreanPrompt, String style, String ratio) {
        String url = "https://api.openai.com/v1/chat/completions";

        String systemMessage = buildSystemMessage(style, ratio);

        // 요청 body 구성
        Map<String, Object> request = new HashMap<>();
        request.put("model", "gpt-4o-mini");
        request.put("max_tokens", 300);
        request.put("temperature", 0.7);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemMessage));
        messages.add(Map.of("role", "user", "content", koreanPrompt));
        request.put("messages", messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openaiApiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        log.debug("Calling OpenAI for prompt enhancement...");

        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                String.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("OpenAI API returned " + response.getStatusCode());
        }

        // 응답 파싱
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                String content = choices.get(0).get("message").get("content").asText();
                return content.trim();
            }
            throw new RuntimeException("No choices in OpenAI response");
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse OpenAI response", e);
        }
    }

    /**
     * 시스템 프롬프트 구성
     * - 번역 + 스타일 보강 + Leonardo AI 최적화 지시
     */
    private String buildSystemMessage(String style, String ratio) {
        String styleGuide = (style != null && !style.isBlank()) ? style : "illustration";

        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert image prompt engineer for Leonardo AI image generation.\n\n");
        sb.append("Your task:\n");
        sb.append("1. Translate the Korean scene description into English.\n");
        sb.append("2. Enhance it into a detailed, high-quality image generation prompt optimized for Leonardo AI.\n");
        sb.append("3. Keep the original scene's meaning and emotion intact.\n\n");

        sb.append("Rules:\n");
        sb.append("- Output ONLY the enhanced English prompt, nothing else.\n");
        sb.append("- Do NOT include any explanation, prefix, or quotes.\n");
        sb.append("- Keep the prompt under 200 words.\n");
        sb.append("- Use descriptive visual language: lighting, composition, mood, colors.\n");
        sb.append("- Include quality boosters: 'high quality', 'detailed', 'professional'.\n");
        sb.append("- If a character name appears (like 민준, 수아, etc.), describe them visually ");
        sb.append("(e.g., 'a young Korean boy' or 'a cheerful Korean girl') instead of using the name.\n");
        sb.append("- Maintain character consistency: describe characters with consistent features.\n\n");

        sb.append("Style: ").append(styleGuide).append("\n");

        if (ratio != null && !ratio.isBlank()) {
            sb.append("Aspect ratio context: ").append(ratio).append("\n");
        }

        sb.append("\nExample:\n");
        sb.append("Input: 민준이 이불 속에서 행복하게 일어나는 모습\n");
        sb.append("Output: A young Korean boy happily waking up in a cozy bed, ");
        sb.append("stretching with a bright cheerful smile, warm morning sunlight streaming through the window, ");
        sb.append("soft pastel-colored blanket and pillows, ");
        sb.append("warm and inviting bedroom setting, ");
        sb.append(styleGuide).append(" style, ");
        sb.append("soft golden lighting, high quality, detailed, professional illustration");

        return sb.toString();
    }

    /**
     * OpenAI 호출 실패 시 기본 폴백 프롬프트 생성
     * (한국어 프롬프트에 스타일 키워드만 추가)
     */
    private String buildFallbackPrompt(String koreanPrompt, String style) {
        String styleGuide = (style != null && !style.isBlank()) ? style : "illustration";

        return koreanPrompt + ", "
                + styleGuide + " style, "
                + "high quality, detailed, professional, "
                + "warm lighting, cinematic composition, "
                + "vibrant colors, masterpiece quality, 4k";
    }
}
