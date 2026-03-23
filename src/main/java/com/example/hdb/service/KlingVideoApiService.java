package com.example.hdb.service;

import com.example.hdb.config.KlingConfig;
import com.example.hdb.dto.kling.KlingVideoRequest;
import com.example.hdb.dto.kling.KlingVideoResponse;
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

    /**
     * 영상 생성 요청
     */
    public KlingVideoResponse generateVideo(KlingVideoRequest request) {
        log.info("Starting Kling video generation request");

        try {
            // Pollo AI Kling 2.0 API 사용
            // 참고: https://docs.pollo.ai/m/kling-ai/kling-v2-0
            String videoUrl = "https://pollo.ai/api/platform/generation/kling-ai/kling-v2";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", klingTokenService.getBearerToken().replace("Bearer ", ""));

            // Pollo API 형식으로 요청 변환
            String requestBody = String.format("""
                {
                    "input": {
                        "prompt": "%s",
                        "negativePrompt": "",
                        "strength": 50,
                        "length": %d
                        %s
                    }
                    %s
                }
                """,
                    request.getPrompt().replace("\"", "\\\""),
                    request.getDuration(),
                    request.getImageUrl() != null ? String.format(", \"image\": \"%s\"", request.getImageUrl()) : "",
                    request.getWebhookUrl() != null ? String.format(", \"webhookUrl\": \"%s\"", request.getWebhookUrl()) : "");

            HttpEntity<String> httpEntity = new HttpEntity<>(requestBody, headers);

            log.info("Kling API request URL: {}", videoUrl);
            log.info("Kling API request body: {}", requestBody);

            ResponseEntity<String> response = restTemplate.postForEntity(videoUrl, httpEntity, String.class);

            log.info("Kling API response status: {}", response.getStatusCode());
            log.info("Kling API response body: {}", response.getBody());

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                // Pollo API 응답 파싱
                KlingVideoResponse klingResponse = parsePolloResponse(response.getBody());
                log.info("Kling video generation request successful. Task ID: {}", klingResponse.getTaskId());
                return klingResponse;
            } else {
                log.error("Kling video generation failed. Status: {}, Body: {}",
                        response.getStatusCode(), response.getBody());
                throw new RuntimeException("Video generation failed");
            }

        } catch (Exception e) {
            log.error("Error calling Kling video generation API", e);
            throw new RuntimeException("Failed to generate video", e);
        }
    }

    /**
     * Pollo API 응답 파싱
     */
    private KlingVideoResponse parsePolloResponse(String responseBody) {
        try {
            // Pollo API 응답 형식: {"taskId": "xxx", "status": "waiting"}
            // 임시 파싱 - 실제로는 Jackson 사용 권장
            String taskId = "temp_task_" + System.currentTimeMillis();
            String status = "waiting";

            if (responseBody.contains("\"taskId\"")) {
                int start = responseBody.indexOf("\"taskId\":\"") + 10;
                int end = responseBody.indexOf("\"", start);
                if (end > start) {
                    taskId = responseBody.substring(start, end);
                }
            }

            if (responseBody.contains("\"status\"")) {
                int start = responseBody.indexOf("\"status\":\"") + 10;
                int end = responseBody.indexOf("\"", start);
                if (end > start) {
                    status = responseBody.substring(start, end);
                }
            }

            KlingVideoResponse response = new KlingVideoResponse();
            response.setTaskId(taskId);
            response.setStatus(status);
            response.setVideoUrl(null); // Pollo API는 즉시 URL을 반환하지 않음

            return response;
        } catch (Exception e) {
            log.error("Error parsing Pollo response", e);
            throw new RuntimeException("Failed to parse response", e);
        }
    }

    /**
     * 영상 생성 상태 조회
     */
    public KlingVideoResponse checkVideoStatus(String taskId) {
        log.info("Checking Kling video status for task: {}", taskId);

        try {
            // Pollo AI 상태 조회 API
            String statusUrl = "https://pollo.ai/api/platform/task/" + taskId;

            HttpHeaders headers = new HttpHeaders();
            headers.set("x-api-key", klingTokenService.getBearerToken().replace("Bearer ", ""));

            HttpEntity<Void> httpEntity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    statusUrl, HttpMethod.GET, httpEntity, String.class);

            log.info("Kling status check response: {}", response.getBody());

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                // Pollo API 상태 응답 파싱
                KlingVideoResponse klingResponse = parsePolloResponse(response.getBody());
                log.info("Kling video status: {}", klingResponse.getStatus());
                return klingResponse;
            } else {
                log.error("Kling video status check failed. Status: {}, Body: {}",
                        response.getStatusCode(), response.getBody());
                throw new RuntimeException("Video status check failed");
            }

        } catch (Exception e) {
            log.error("Error checking Kling video status", e);
            throw new RuntimeException("Failed to check video status", e);
        }
    }
}