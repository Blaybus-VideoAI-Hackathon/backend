package com.example.hdb.service;

import com.example.hdb.config.KlingConfig;
import com.example.hdb.dto.kling.KlingVideoRequest;
import com.example.hdb.dto.kling.KlingVideoResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class KlingVideoApiService {

    private final KlingConfig klingConfig;
    private final KlingTokenService klingTokenService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // Kling 공식 API 엔드포인트
    private static final String BASE_URL = "https://api.klingai.com";
    private static final String TEXT_TO_VIDEO_URL = BASE_URL + "/v1/videos/text2video";
    private static final String IMAGE_TO_VIDEO_URL = BASE_URL + "/v1/videos/image2video";

    // ──────────────────────────────────────────
    // 영상 생성 요청
    // ──────────────────────────────────────────

    public KlingVideoResponse generateVideo(KlingVideoRequest request) {
        log.info("=== Kling Video Generation Started ===");
        log.info("prompt={}, duration={}, hasImage={}",
                request.getPrompt(), request.getDuration(), request.getImageUrl() != null);

        try {
            HttpHeaders headers = buildHeaders();

            boolean hasImage = isUsableImageUrl(request.getImageUrl());
            String apiUrl = hasImage ? IMAGE_TO_VIDEO_URL : TEXT_TO_VIDEO_URL;

            // Kling API는 duration을 "5" 또는 "10"만 허용
            String klingDuration = toKlingDuration(request.getDuration());
            log.info("Kling duration: {} → '{}'", request.getDuration(), klingDuration);

            String requestBody = hasImage
                    ? buildImageToVideoBody(request, klingDuration)
                    : buildTextToVideoBody(request, klingDuration);

            log.info("Kling API URL: {}", apiUrl);
            log.info("Kling request body: {}", requestBody);

            HttpEntity<String> httpEntity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, httpEntity, String.class);

            log.info("Kling API response status: {}", response.getStatusCode());
            log.info("Kling API response body: {}", response.getBody());

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                KlingVideoResponse result = parseKlingResponse(response.getBody());
                log.info("Kling video task created. taskId={}", result.getTaskId());
                return result;
            } else {
                log.error("Kling API failed. status={}, body={}", response.getStatusCode(), response.getBody());
                throw new RuntimeException("Kling video generation failed: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Error calling Kling video generation API", e);
            throw new RuntimeException("Failed to generate video", e);
        }
    }

    // ──────────────────────────────────────────
    // 영상 생성 상태 조회
    // ──────────────────────────────────────────

    public KlingVideoResponse checkVideoStatus(String taskId) {
        log.info("=== Kling Video Status Check === taskId={}", taskId);

        try {
            HttpHeaders headers = buildHeaders();
            HttpEntity<Void> httpEntity = new HttpEntity<>(headers);

            String statusUrl = BASE_URL + "/v1/videos/text2video/" + taskId;

            ResponseEntity<String> response = restTemplate.exchange(
                    statusUrl, HttpMethod.GET, httpEntity, String.class);

            log.info("Kling status response: {}", response.getBody());

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseKlingStatusResponse(response.getBody());
            } else {
                log.error("Kling status check failed. status={}", response.getStatusCode());
                throw new RuntimeException("Kling status check failed");
            }

        } catch (Exception e) {
            log.error("Error checking Kling video status for taskId={}", taskId, e);
            throw new RuntimeException("Failed to check video status", e);
        }
    }

    // ──────────────────────────────────────────
    // PRIVATE: 요청 빌더
    // ──────────────────────────────────────────

    private HttpHeaders buildHeaders() {
        String token = klingTokenService.getBearerToken();
        log.info("Kling JWT token prefix: {}...", token.substring(0, Math.min(20, token.length())));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", token);
        return headers;
    }

    /**
     * Kling API duration 허용값 변환
     * Kling 공식 API: "5" 또는 "10"만 허용
     * 5초 이하 → "5", 초과 → "10"
     */
    private String toKlingDuration(Integer duration) {
        if (duration == null || duration <= 5) {
            return "5";
        }
        return "10";
    }

    /**
     * text-to-video 요청 바디
     */
    private String buildTextToVideoBody(KlingVideoRequest request, String duration) {
        String safePrompt = sanitizePrompt(request.getPrompt());
        String aspectRatio = request.getAspectRatio() != null ? request.getAspectRatio() : "16:9";

        return String.format("""
                {
                  "model": "kling-v1",
                  "prompt": "%s",
                  "negative_prompt": "",
                  "cfg_scale": 0.5,
                  "mode": "std",
                  "aspect_ratio": "%s",
                  "duration": "%s"
                }
                """, safePrompt, aspectRatio, duration);
    }

    /**
     * image-to-video 요청 바디
     */
    private String buildImageToVideoBody(KlingVideoRequest request, String duration) {
        String safePrompt = sanitizePrompt(request.getPrompt());

        return String.format("""
                {
                  "model": "kling-v1",
                  "image_url": "%s",
                  "prompt": "%s",
                  "negative_prompt": "",
                  "cfg_scale": 0.5,
                  "mode": "std",
                  "duration": "%s"
                }
                """, request.getImageUrl(), safePrompt, duration);
    }

    // ──────────────────────────────────────────
    // PRIVATE: 응답 파서
    // ──────────────────────────────────────────

    /**
     * 영상 생성 응답 파싱
     * {
     *   "code": 0,
     *   "message": "SUCCEED",
     *   "data": {
     *     "task_id": "xxx",
     *     "task_status": "submitted"
     *   }
     * }
     */
    private KlingVideoResponse parseKlingResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            int code = root.path("code").asInt(-1);
            String message = root.path("message").asText("");

            if (code != 0) {
                log.error("Kling API error code={}, message={}", code, message);
                throw new RuntimeException("Kling API error: " + message);
            }

            JsonNode data = root.path("data");
            String taskId = data.path("task_id").asText("temp_" + System.currentTimeMillis());
            String taskStatus = data.path("task_status").asText("submitted");

            KlingVideoResponse result = new KlingVideoResponse();
            result.setTaskId(taskId);
            result.setStatus(taskStatus);
            result.setVideoUrl(null);

            log.info("Kling task created - taskId={}, status={}", taskId, taskStatus);
            return result;

        } catch (Exception e) {
            log.error("Failed to parse Kling response: {}", responseBody, e);
            throw new RuntimeException("Failed to parse Kling response", e);
        }
    }

    /**
     * 영상 상태 조회 응답 파싱
     * {
     *   "code": 0,
     *   "data": {
     *     "task_id": "xxx",
     *     "task_status": "succeed",
     *     "task_result": {
     *       "videos": [{ "url": "https://..." }]
     *     }
     *   }
     * }
     */
    private KlingVideoResponse parseKlingStatusResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            int code = root.path("code").asInt(-1);
            if (code != 0) {
                log.error("Kling status API error code={}", code);
                throw new RuntimeException("Kling status API error: " + root.path("message").asText());
            }

            JsonNode data = root.path("data");
            String taskId = data.path("task_id").asText("");
            String taskStatus = data.path("task_status").asText("processing");

            KlingVideoResponse result = new KlingVideoResponse();
            result.setTaskId(taskId);
            result.setStatus(taskStatus);

            // 완료 시 video URL 추출
            if ("succeed".equalsIgnoreCase(taskStatus)) {
                JsonNode videos = data.path("task_result").path("videos");
                if (videos.isArray() && videos.size() > 0) {
                    String videoUrl = videos.get(0).path("url").asText("");
                    if (!videoUrl.isBlank()) {
                        result.setVideoUrl(videoUrl);
                        log.info("Kling video completed - taskId={}, videoUrl={}", taskId, videoUrl);
                    }
                }
            } else {
                log.info("Kling video status - taskId={}, status={}", taskId, taskStatus);
            }

            return result;

        } catch (Exception e) {
            log.error("Failed to parse Kling status response: {}", responseBody, e);
            throw new RuntimeException("Failed to parse Kling status response", e);
        }
    }

    // ──────────────────────────────────────────
    // PRIVATE: 유틸
    // ──────────────────────────────────────────

    private boolean isUsableImageUrl(String imageUrl) {
        return imageUrl != null
                && !imageUrl.isBlank()
                && !imageUrl.contains("picsum.photos")
                && !imageUrl.contains("fallback-image-service.com");
    }

    private String sanitizePrompt(String prompt) {
        if (prompt == null) return "cinematic video";
        return prompt
                .replace("\"", "'")
                .replace("\n", " ")
                .replace("\r", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}