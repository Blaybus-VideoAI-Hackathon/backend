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
            String videoUrl = klingConfig.getBaseUrl() + "/v1/videos/generate";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(klingTokenService.getBearerToken().replace("Bearer ", ""));
            
            HttpEntity<KlingVideoRequest> httpEntity = new HttpEntity<>(request, headers);
            
            ResponseEntity<KlingVideoResponse> response = restTemplate.postForEntity(
                    videoUrl, httpEntity, KlingVideoResponse.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                log.info("Kling video generation request successful. Task ID: {}", 
                        response.getBody().getTaskId());
                return response.getBody();
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
     * 영상 생성 상태 조회
     */
    public KlingVideoResponse checkVideoStatus(String taskId) {
        log.info("Checking Kling video status for task: {}", taskId);
        
        try {
            String statusUrl = klingConfig.getBaseUrl() + "/v1/videos/status/" + taskId;
            
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(klingTokenService.getBearerToken().replace("Bearer ", ""));
            
            HttpEntity<Void> httpEntity = new HttpEntity<>(headers);
            
            ResponseEntity<KlingVideoResponse> response = restTemplate.exchange(
                    statusUrl, HttpMethod.GET, httpEntity, KlingVideoResponse.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                log.info("Kling video status check successful. Status: {}, Task: {}", 
                        response.getBody().getStatus(), taskId);
                return response.getBody();
            } else {
                log.error("Kling video status check failed. Status: {}, Body: {}", 
                        response.getStatusCode(), response.getBody());
                throw new RuntimeException("Video status check failed");
            }
            
        } catch (Exception e) {
            log.error("Error checking Kling video status for task: {}", taskId, e);
            throw new RuntimeException("Failed to check video status", e);
        }
    }
}
