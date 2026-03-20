package com.example.hdb.controller;

import com.example.hdb.dto.common.ApiResponse;
import com.example.hdb.dto.request.ImageEditCompleteRequest;
import com.example.hdb.dto.request.SceneImageEditAiRequest;
import com.example.hdb.dto.response.SceneImageEditAiResponse;
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
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
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
    
    // ========== 이미지 편집 관련 API ==========
    
    @Operation(summary = "이미지 편집 완료", description = "편집된 이미지를 서버에 저장하고 editedImageUrl을 설정합니다.")
    @PostMapping("/{imageId}/edit/complete")
    public ResponseEntity<ApiResponse<SceneImageResponse>> completeImageEdit(
            @Parameter(description = "프로젝트 ID") @PathVariable Long projectId,
            @Parameter(description = "씬 ID") @PathVariable Long sceneId,
            @Parameter(description = "이미지 ID") @PathVariable Long imageId,
            @Valid @RequestBody ImageEditCompleteRequest request,
            Authentication authentication) {
        
        log.info("Completing image edit for imageId: {}, projectId: {}, sceneId: {}", imageId, projectId, sceneId);
        
        String loginId = resolveLoginId(authentication);
        SceneImageResponse response = sceneImageService.completeImageEdit(projectId, sceneId, imageId, loginId, request);
        
        return ResponseEntity.ok(ApiResponse.success("이미지 편집 완료본이 저장되었습니다.", response));
    }
    
    @Operation(summary = "AI 이미지 편집 제안", description = "이미지 편집을 위한 AI 수정 제안을 제공합니다.")
    @PostMapping("/{imageId}/edit/ai")
    public ResponseEntity<ApiResponse<SceneImageEditAiResponse>> getImageEditAiSuggestions(
            @Parameter(description = "프로젝트 ID") @PathVariable Long projectId,
            @Parameter(description = "씬 ID") @PathVariable Long sceneId,
            @Parameter(description = "이미지 ID") @PathVariable Long imageId,
            @Valid @RequestBody SceneImageEditAiRequest request,
            Authentication authentication) {
        
        log.info("Getting AI edit suggestions for imageId: {}, userEditText: {}", imageId, request.getUserEditText());
        
        String loginId = resolveLoginId(authentication);
        SceneImageEditAiResponse response = sceneImageService.getImageEditAiSuggestions(projectId, sceneId, imageId, loginId, request);
        
        return ResponseEntity.ok(ApiResponse.success("AI 편집 제안 생성 성공", response));
    }
}
