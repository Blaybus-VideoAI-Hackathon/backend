package com.example.hdb.controller;

import com.example.hdb.dto.common.ApiResponse;
import com.example.hdb.dto.request.ImageEditCompleteRequest;
import com.example.hdb.dto.request.SceneImageEditAiRequest;
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
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@Slf4j
public class SceneImageController extends BaseController {
    
    private final SceneImageService sceneImageService;
    
    @Operation(summary = "프로젝트 전체 이미지 목록 조회", description = "프로젝트에 속한 모든 씬의 이미지 목록을 조회합니다.")
    @GetMapping("/{projectId}/images")
    public ResponseEntity<ApiResponse<List<SceneImageResponse>>> getProjectImages(
            @Parameter(description = "프로젝트 ID") @PathVariable Long projectId) {
        
        log.info("Getting all images for project: {}", projectId);
        
        String loginId = "user1"; // 임시 fallback
        List<SceneImageResponse> images = sceneImageService.getProjectImages(projectId, loginId);
        
        return ResponseEntity.ok(ApiResponse.success("프로젝트 이미지 목록 조회 성공", images));
    }
    
    @Operation(summary = "씬 이미지 생성", description = "씬의 imagePrompt를 기반으로 이미지를 생성합니다.")
    @PostMapping("/{projectId}/scenes/{sceneId}/images/generate")
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
    @GetMapping("/{projectId}/scenes/{sceneId}/images")
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
    @PostMapping("/{projectId}/scenes/{sceneId}/images/{imageId}/edit/complete")
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
    
    @Operation(summary = "AI 이미지 편집 및 새 이미지 생성", description = "이미지를 AI로 편집하여 새 이미지를 생성하고 저장합니다.")
    @PostMapping("/{projectId}/scenes/{sceneId}/images/{imageId}/edit/ai")
    public ResponseEntity<ApiResponse<SceneImageResponse>> generateImageEditAi(
            @Parameter(description = "프로젝트 ID") @PathVariable Long projectId,
            @Parameter(description = "씬 ID") @PathVariable Long sceneId,
            @Parameter(description = "원본 이미지 ID") @PathVariable Long imageId,
            @Valid @RequestBody SceneImageEditAiRequest request,
            Authentication authentication) {
        
        log.info("=== AI Image Edit Generation Started ===");
        log.info("projectId: {}, sceneId: {}, imageId: {}, userEditText: {}", 
                projectId, sceneId, imageId, request.getUserEditText());
        
        String loginId = resolveLoginId(authentication);
        SceneImageResponse response = sceneImageService.generateImageEditAi(projectId, sceneId, imageId, loginId, request);
        
        return ResponseEntity.ok(ApiResponse.success("AI 수정 이미지 생성 완료", response));
    }
}
