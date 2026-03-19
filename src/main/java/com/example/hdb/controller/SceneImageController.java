package com.example.hdb.controller;

import com.example.hdb.dto.common.ApiResponse;
import com.example.hdb.dto.response.SceneImageResponse;
import com.example.hdb.service.SceneImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/scenes/{sceneId}/images")
@RequiredArgsConstructor
@Slf4j
public class SceneImageController extends BaseController {
    
    private final SceneImageService sceneImageService;
    
    @Operation(summary = "씬 이미지 생성", description = "씬의 imagePrompt를 기반으로 이미지를 생성합니다.")
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<SceneImageResponse>> generateImage(
            @Parameter(description = "프로젝트 ID") @PathVariable Long projectId,
            @Parameter(description = "씬 ID") @PathVariable Long sceneId,
            Authentication authentication) {
        
        log.info("=== SceneImage Generation Started ===");
        log.info("projectId: {}", projectId);
        log.info("sceneId: {}", sceneId);
        
        try {
            String loginId = resolveLoginId(authentication);
            log.info("resolved loginId: {}", loginId);
            
            SceneImageResponse response = sceneImageService.generateImage(projectId, sceneId, loginId);
            
            log.info("SceneImage generation completed successfully");
            return ResponseEntity.ok(ApiResponse.success("이미지 생성 성공", response));
            
        } catch (Exception e) {
            log.error("SceneImage generation failed", e);
            throw e; // rethrow to maintain original error behavior
        }
    }
    
    @Operation(summary = "씬 이미지 목록 조회", description = "씬에 생성된 모든 이미지를 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<SceneImageResponse>>> getImages(
            @Parameter(description = "프로젝트 ID") @PathVariable Long projectId,
            @Parameter(description = "씬 ID") @PathVariable Long sceneId,
            Authentication authentication) {
        
        log.info("Getting images for scene: {} in project: {}", sceneId, projectId);
        
        String loginId = resolveLoginId(authentication);
        List<SceneImageResponse> images = sceneImageService.getImages(projectId, sceneId, loginId);
        
        return ResponseEntity.ok(ApiResponse.success("이미지 목록 조회 성공", images));
    }
}
