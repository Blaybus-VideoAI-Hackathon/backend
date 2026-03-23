package com.example.hdb.service;

import com.example.hdb.config.RunwayConfig;
import com.example.hdb.dto.runway.RunwayVideoRequest;
import com.example.hdb.dto.runway.RunwayVideoResponse;
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
public class RunwayVideoApiService {

    private final RunwayConfig runwayConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // Runway API 엔드포인트
    private static final String IMAGE_TO_VIDEO_URL = "https://api.dev.runwayml.com/v1/image_to_video";
    private static final String TASK_STATUS_URL = "https://api.dev.runwayml.com/v1/tasks/";

    // ──────────────────────────────────────────
    // 영상 생성 태스크 생성
    // ──────────────────────────────────────────

    public RunwayVideoResponse createTask(RunwayVideoRequest request) {
        log.info("=== Runway Video Task Creation Started ===");
        log.info("promptText={}, hasImage={}, duration={}",
                request.getPromptText(), request.getPromptImage() != null, request.getDuration());

        try {
            HttpHeaders headers = buildHeaders();
            
            // 공통 요청 바디
            RunwayVideoRequest.RunwayRequestBody body = new RunwayVideoRequest.RunwayRequestBody();
            body.setModel("gen4.5");
            body.setPromptText(request.getPromptText());
            body.setRatio("1280:720");
            body.setDuration(5);

            // 이미지가 있으면 image-to-video 모드
            if (request.getPromptImage() != null && !request.getPromptImage().isBlank()) {
                body.setPromptImage(request.getPromptImage());
                log.info("Using image-to-video mode with promptImage: {}", request.getPromptImage());
            } else {
                log.info("Using text-to-video mode (no promptImage)");
            }

            HttpEntity<RunwayVideoRequest.RunwayRequestBody> httpEntity = new HttpEntity<>(body, headers);

            log.info("Runway API URL: {}", IMAGE_TO_VIDEO_URL);
            log.info("Request body: {}", objectMapper.writeValueAsString(body));

            ResponseEntity<String> response = restTemplate.postForEntity(
                    IMAGE_TO_VIDEO_URL, httpEntity, String.class);

            log.info("Runway API response status: {}", response.getStatusCode());
            log.info("Runway API response body: {}", response.getBody());

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RuntimeException("Runway API failed: " + response.getStatusCode());
            }

            RunwayVideoResponse result = parseCreateTaskResponse(response.getBody());
            log.info("Runway task created. id={}", result.getId());
            return result;

        } catch (Exception e) {
            log.error("Error calling Runway video generation API", e);
            throw new RuntimeException("Failed to create video task", e);
        }
    }

    // ──────────────────────────────────────────
    // 태스크 상태 조회
    // ──────────────────────────────────────────

    public RunwayVideoResponse getTaskStatus(String taskId) {
        log.info("=== Runway Video Task Status Check === taskId={}", taskId);

        try {
            HttpHeaders headers = buildHeaders();
            HttpEntity<Void> httpEntity = new HttpEntity<>(headers);

            String statusUrl = TASK_STATUS_URL + taskId;

            ResponseEntity<String> response = restTemplate.exchange(
                    statusUrl, HttpMethod.GET, httpEntity, String.class);

            log.info("Runway status response: {}", response.getBody());

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RuntimeException("Runway status check failed: " + response.getStatusCode());
            }

            return parseStatusResponse(response.getBody());

        } catch (Exception e) {
            log.error("Error checking Runway video status for taskId={}", taskId, e);
            throw new RuntimeException("Failed to check task status", e);
        }
    }

    // ──────────────────────────────────────────
    // PRIVATE: 요청 빌더
    // ──────────────────────────────────────────

    private HttpHeaders buildHeaders() {
        String apiKey = runwayConfig.getKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("RUNWAY_API_KEY not configured");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);
        headers.set("X-Runway-Version", "2024-11-06");
        return headers;
    }

    // ──────────────────────────────────────────
    // PRIVATE: 응답 파서
    // ──────────────────────────────────────────

    private RunwayVideoResponse parseCreateTaskResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            
            String taskId = root.path("id").asText("");
            String status = normalizeStatus(root.path("status").asText(""));

            RunwayVideoResponse response = new RunwayVideoResponse();
            response.setId(taskId);
            response.setStatus(status);
            response.setVideoUrl(null); // 생성 시점엔 URL 없음
            
            return response;

        } catch (Exception e) {
            log.error("Failed to parse Runway create task response: {}", responseBody, e);
            throw new RuntimeException("Failed to parse response", e);
        }
    }

    private RunwayVideoResponse parseStatusResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            
            String taskId = root.path("id").asText("");
            String status = normalizeStatus(root.path("status").asText(""));
            String videoUrl = extractVideoUrl(root);

            RunwayVideoResponse response = new RunwayVideoResponse();
            response.setId(taskId);
            response.setStatus(status);
            response.setVideoUrl(videoUrl);
            
            return response;

        } catch (Exception e) {
            log.error("Failed to parse Runway status response: {}", responseBody, e);
            throw new RuntimeException("Failed to parse response", e);
        }
    }

    // ──────────────────────────────────────────
    // PRIVATE: 유틸
    // ──────────────────────────────────────────

    private String normalizeStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return "GENERATING";
        }

        String value = rawStatus.trim().toLowerCase();

        return switch (value) {
            case "starting", "pending", "processing", "throttled", "queued" -> "GENERATING";
            case "succeeded" -> "COMPLETED";
            case "failed", "cancelled" -> "FAILED";
            default -> value.toUpperCase();
        };
    }

    private String extractVideoUrl(JsonNode root) {
        // output -> 관련 비디오 결과 필드에서 URL 추출
        JsonNode output = root.path("output");
        if (output.isMissingNode() || output.isEmpty()) {
            return null;
        }

        // 여러 가능한 필드 확인
        JsonNode videoNode = output.path("video");
        if (!videoNode.isMissingNode() && videoNode.isTextual()) {
            String videoUrl = videoNode.asText();
            log.info("Found video URL from output.video: {}", videoUrl);
            return videoUrl;
        }

        // 다른 가능한 필드들 확인
        for (JsonNode child : output) {
            if (child.has("url") && child.get("url").isTextual()) {
                String url = child.get("url").asText();
                log.info("Found video URL from output array: {}", url);
                return url;
            }
        }

        return null;
    }
}
