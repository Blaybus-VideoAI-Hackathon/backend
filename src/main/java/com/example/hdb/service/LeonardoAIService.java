package com.example.hdb.service;

import com.example.hdb.dto.response.ImageGenerationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Leonardo AI Image Generation Service
 *
 * ★ 일관성 전략: Seed 기반 일관성
 * - 첫 씬: 표준 생성 (seed 저장)
 * - 다음 씬: 첫 씬의 seed 재사용 → 같은 스타일, 분위기 유지
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "leonardo.api-key", matchIfMissing = false)
public class LeonardoAIService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${leonardo.api-key:}")
    private String apiKey;

    @Value("${leonardo.api-url:https://cloud.leonardo.ai/api/rest/v1}")
    private String apiUrl;

    @Value("${leonardo.model-id:7b592283-e8a7-4c5a-9ba6-d18c31f258b9}")
    private String modelId;

    /**
     * 표준 이미지 생성
     */
    public ImageGenerationResult generateImage(String imagePrompt, String consistencyMode, String refImageUrl) {
        log.info("=== Leonardo AI Standard Image Generation ===");
        log.info("Prompt length: {}", imagePrompt.length());

        try {
            Map<String, Object> request = buildRequest(imagePrompt);
            Map<String, Object> response = callAPI(request);

            String generationId = parseGenerationId(response);
            if (generationId == null) {
                throw new RuntimeException("No generationId in response");
            }

            log.info("Generation ID: {}", generationId);

            Map<String, Object> result = pollResult(generationId);
            String imageUrl = parseImageUrl(result);
            String seed = parseSeed(result);

            if (imageUrl == null) {
                throw new RuntimeException("No image URL in result");
            }

            log.info("Image generated successfully");
            log.info("Seed: {}", seed);

            return ImageGenerationResult.builder()
                    .imageUrl(imageUrl)
                    .revisedPrompt(imagePrompt)
                    .generationId(generationId)
                    .seed(seed)
                    .model("lucid-origin")
                    .build();

        } catch (Exception e) {
            log.error("Image generation failed", e);
            throw new RuntimeException("Leonardo AI generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * ★ 일관성 있는 이미지 생성 (Seed 기반)
     *
     * 첫 씬의 seed를 재사용하여 스타일, 분위기, 색감이 일관된 이미지 생성
     */
    public ImageGenerationResult generateConsistentImage(
            String imagePrompt,
            String firstSceneImageUrl,
            String firstSceneSeed) {

        log.info("=== Leonardo AI Consistent Image Generation (Seed-based) ===");
        log.info("Reference Seed: {}", firstSceneSeed);
        log.info("Reference Image URL: {}", firstSceneImageUrl);

        try {
            // Seed가 있으면 seed 기반으로, 없으면 일반 생성
            if (firstSceneSeed != null && !firstSceneSeed.isBlank()) {
                return generateImageWithSeed(imagePrompt, firstSceneSeed);
            } else {
                log.warn("No seed available, using standard generation");
                return generateImage(imagePrompt, "STANDARD", null);
            }

        } catch (Exception e) {
            log.error("Consistent image generation failed", e);
            // Fallback: 일반 생성으로 대체
            log.warn("Falling back to standard generation");
            return generateImage(imagePrompt, "STANDARD", null);
        }
    }

    /**
     * Seed 기반 이미지 생성 (일관성 유지)
     *
     * 같은 seed를 사용하면 Leonardo AI는 같은 스타일, 분위기, 색감의 이미지를 생성합니다.
     */
    private ImageGenerationResult generateImageWithSeed(String imagePrompt, String firstSceneSeed) {
        log.info("=== Generating image with seed: {} ===", firstSceneSeed);

        try {
            // Seed를 정수로 변환
            long seedValue = parseSeedToLong(firstSceneSeed);
            log.info("Parsed seed value: {}", seedValue);

            // Seed를 포함한 요청 생성
            Map<String, Object> request = buildRequest(imagePrompt);
            request.put("seed", seedValue);

            log.debug("Request with seed: {}", request);

            Map<String, Object> response = callAPI(request);
            String generationId = parseGenerationId(response);

            if (generationId == null) {
                throw new RuntimeException("No generationId in response");
            }

            log.info("Generation ID: {}", generationId);

            // 폴링으로 완성 대기
            Map<String, Object> result = pollResult(generationId);
            String imageUrl = parseImageUrl(result);
            String generatedSeed = parseSeed(result);

            if (imageUrl == null) {
                throw new RuntimeException("No image URL in result");
            }

            log.info("Consistent image generated with seed");
            log.info("Generated seed: {}", generatedSeed);

            return ImageGenerationResult.builder()
                    .imageUrl(imageUrl)
                    .revisedPrompt(imagePrompt)
                    .generationId(generationId)
                    .seed(generatedSeed != null ? generatedSeed : firstSceneSeed)
                    .model("lucid-origin")
                    .build();

        } catch (Exception e) {
            log.error("Seed-based generation failed", e);
            throw new RuntimeException("Failed to generate with seed", e);
        }
    }

    /**
     * Seed 문자열을 long으로 변환
     */
    private long parseSeedToLong(String seed) {
        try {
            return Long.parseLong(seed.trim());
        } catch (NumberFormatException e) {
            log.warn("Seed is not a pure number, using hash: {}", seed);
            // Seed가 해시 형식인 경우, hashCode 사용
            return Math.abs((long) seed.hashCode());
        }
    }

    /**
     * 기본 요청 본체 생성
     */
    private Map<String, Object> buildRequest(String prompt) {
        Map<String, Object> req = new HashMap<>();

        req.put("modelId", modelId);
        req.put("prompt", prompt);
        req.put("width", 1440);
        req.put("height", 1440);
        req.put("num_images", 1);

        // 생성 파라미터
        req.put("guidance_scale", 7.0);
        req.put("contrast", 3.5);
        req.put("alchemy", false);
        req.put("ultra", false);

        return req;
    }

    /**
     * Leonardo AI API 호출
     */
    private Map<String, Object> callAPI(Map<String, Object> request) {
        try {
            String url = apiUrl + "/generations";
            log.debug("POST {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("HTTP " + response.getStatusCode());
            }

            Map<String, Object> body = response.getBody();
            if (body == null) {
                throw new RuntimeException("Empty response");
            }

            return body;

        } catch (Exception e) {
            log.error("API call failed", e);
            throw new RuntimeException("API call failed", e);
        }
    }

    /**
     * 생성 결과 폴링 (최대 120초)
     */
    private Map<String, Object> pollResult(String generationId) {
        log.info("Polling generation: {}", generationId);

        for (int i = 0; i < 120; i++) {
            try {
                Thread.sleep(1000);

                String url = apiUrl + "/generations/" + generationId;
                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "Bearer " + apiKey);

                ResponseEntity<Map> response = restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        Map.class
                );

                if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                    continue;
                }

                Map<String, Object> body = response.getBody();

                // 상태 확인
                Object generationsByPk = body.get("generations_by_pk");
                if (generationsByPk instanceof Map) {
                    Map<String, Object> genMap = (Map<String, Object>) generationsByPk;
                    Object statusObj = genMap.get("status");

                    if (statusObj != null) {
                        String status = statusObj.toString();

                        if ("COMPLETED".equalsIgnoreCase(status) ||
                                "COMPLETE".equalsIgnoreCase(status) ||
                                "FINISHED".equalsIgnoreCase(status)) {
                            log.info("Generation complete");
                            return body;
                        }

                        if ("FAILED".equalsIgnoreCase(status) || "ERROR".equalsIgnoreCase(status)) {
                            throw new RuntimeException("Generation failed: " + status);
                        }

                        if (i % 30 == 0) {
                            log.debug("Poll {}: status={}", i, status);
                        }
                    }
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Polling interrupted", e);
            } catch (Exception e) {
                if (i < 3) {
                    log.debug("Poll attempt {} failed", i);
                }
            }
        }

        throw new RuntimeException("Generation timeout");
    }

    /**
     * GenerationId 파싱
     */
    private String parseGenerationId(Map<String, Object> response) {
        // sdGenerationJob 확인 (REST API 응답 형식)
        Object sdJob = response.get("sdGenerationJob");
        if (sdJob instanceof Map) {
            Map<String, Object> sdJobMap = (Map<String, Object>) sdJob;
            for (String key : new String[]{"generationId", "generation_id", "id"}) {
                Object val = sdJobMap.get(key);
                if (val != null && !val.toString().isBlank()) {
                    log.debug("Found sdGenerationJob.{} = {}", key, val);
                    return val.toString();
                }
            }
        }

        // 직접 필드 확인
        for (String key : new String[]{"generationId", "generation_id", "id"}) {
            Object val = response.get(key);
            if (val != null && !val.toString().isBlank()) {
                log.debug("Found {} = {}", key, val);
                return val.toString();
            }
        }

        log.error("Could not find generationId");
        return null;
    }

    /**
     * 이미지 URL 파싱
     */
    private String parseImageUrl(Map<String, Object> response) {
        // generations_by_pk.generated_images 구조
        Object generationsByPk = response.get("generations_by_pk");
        if (generationsByPk instanceof Map) {
            Map<String, Object> genMap = (Map<String, Object>) generationsByPk;
            Object generatedImages = genMap.get("generated_images");

            if (generatedImages instanceof List) {
                List<?> images = (List<?>) generatedImages;
                if (!images.isEmpty() && images.get(0) instanceof Map) {
                    Map<String, Object> image = (Map<String, Object>) images.get(0);
                    Object url = image.get("url");
                    if (url != null && !url.toString().isBlank()) {
                        log.debug("Found URL in generated_images");
                        return url.toString();
                    }
                }
            }
        }

        // Fallback: generation_elements
        Object elementsObj = response.get("generation_elements");
        if (elementsObj instanceof List) {
            List<?> elements = (List<?>) elementsObj;
            if (!elements.isEmpty() && elements.get(0) instanceof Map) {
                Map<String, Object> elem = (Map<String, Object>) elements.get(0);
                Object url = elem.get("url");
                if (url != null && !url.toString().isBlank()) {
                    return url.toString();
                }
            }
        }

        log.error("Could not find image URL");
        return null;
    }

    /**
     * Seed 파싱
     */
    private String parseSeed(Map<String, Object> response) {
        try {
            // generations_by_pk.seed
            Object generationsByPk = response.get("generations_by_pk");
            if (generationsByPk instanceof Map) {
                Map<String, Object> genMap = (Map<String, Object>) generationsByPk;
                Object seed = genMap.get("seed");
                if (seed != null) {
                    log.debug("Found seed: {}", seed);
                    return seed.toString();
                }
            }

            // Fallback: generation_elements[0].seed
            Object elementsObj = response.get("generation_elements");
            if (elementsObj instanceof List) {
                List<?> elements = (List<?>) elementsObj;
                if (!elements.isEmpty() && elements.get(0) instanceof Map) {
                    Map<String, Object> elem = (Map<String, Object>) elements.get(0);
                    Object seed = elem.get("seed");
                    if (seed != null) {
                        return seed.toString();
                    }
                }
            }

            log.warn("Could not find seed");
            return null;

        } catch (Exception e) {
            log.warn("Failed to parse seed", e);
            return null;
        }
    }
}