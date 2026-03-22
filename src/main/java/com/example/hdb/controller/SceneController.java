package com.example.hdb.controller;

import com.example.hdb.dto.common.ApiResponse;
import com.example.hdb.dto.request.SceneCreateRequest;
import com.example.hdb.dto.request.SceneDesignRegenerateRequest;
import com.example.hdb.dto.request.SceneDesignRequest;
import com.example.hdb.dto.request.SceneEditRequest;
import com.example.hdb.dto.request.SceneGenerateRequest;
import com.example.hdb.dto.request.SceneUpdateRequest;
import com.example.hdb.dto.response.SceneDesignResponse;
import com.example.hdb.dto.response.SceneResponse;
import com.example.hdb.service.SceneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/scenes")
@RequiredArgsConstructor
@Slf4j
@Validated
public class SceneController extends BaseController {
    
    private final SceneService sceneService;
    
    @PostMapping
    @Operation(summary = "씬 생성", description = "새로운 씬을 생성합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "씬 생성 성공")
    public ResponseEntity<ApiResponse<SceneResponse>> createScene(
            @Valid @RequestBody SceneCreateRequest request) {
        
        log.info("Creating scene with projectId: {}, sceneOrder: {}", 
            request.getProjectId(), request.getSceneOrder());
        
        SceneResponse response = sceneService.createScene(request);
        
        return ResponseEntity.status(201)
                .body(ApiResponse.success("씬 생성 성공", response));
    }
    
    @PutMapping("/{sceneId}")
    @Operation(summary = "씬 수정", description = "씬 정보를 수정합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "씬 수정 성공")
    public ResponseEntity<ApiResponse<SceneResponse>> updateScene(
            @Parameter(description = "씬 ID", required = true)
            @PathVariable Long sceneId,
            @Valid @RequestBody SceneUpdateRequest request) {
        
        log.info("Updating scene with id: {}", sceneId);
        SceneResponse response = sceneService.updateScene(sceneId, request);
        
        return ResponseEntity.ok()
                .body(ApiResponse.success("씬 수정 성공", response));
    }
    
    @PutMapping("/{sceneId}/secure")
    @Operation(summary = "씬 수정 (권한 검증)", description = "씬 정보를 수정합니다. 사용자 권한을 검증합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "씬 수정 성공")
    public ResponseEntity<ApiResponse<SceneResponse>> updateSceneAndCheckPermission(
            @Parameter(description = "씬 ID", required = true)
            @PathVariable Long sceneId,
            @Parameter(description = "사용자 ID", required = true)
            @RequestParam Long userId,
            @Valid @RequestBody SceneUpdateRequest request) {
        
        log.info("Updating scene with id: {} for userId: {}", sceneId, userId);
        SceneResponse response = sceneService.updateSceneAndCheckPermission(sceneId, userId, request);
        
        return ResponseEntity.ok()
                .body(ApiResponse.success("씬 수정 성공", response));
    }
    
    // ========== 신규 API (Scene 기능 확장) ==========
    
    @Operation(summary = "씬 자동 생성", description = "프로젝트 기획 기반으로 씬을 자동 생성합니다.")
    @PostMapping("/projects/{projectId}/scenes/generate")
    public ResponseEntity<ApiResponse<List<SceneResponse>>> generateScenes(
            @Parameter(description = "프로젝트 ID") @PathVariable Long projectId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false) @Valid SceneGenerateRequest request,
            Authentication authentication) {
        
        log.info("=== Scene Generation Started ===");
        log.info("Project ID: {}", projectId);
        log.info("Request object: {}", request);
        log.info("Request null: {}", request == null);
        
        // sceneGenerationRequest가 null 또는 empty일 경우 기본값 처리
        String sceneGenerationRequest;
        if (request == null || request.getSceneGenerationRequest() == null || request.getSceneGenerationRequest().trim().isEmpty()) {
            sceneGenerationRequest = "프로젝트 기획을 기반으로 씬을 생성해주세요";
            log.info("Using default sceneGenerationRequest: {}", sceneGenerationRequest);
        } else {
            sceneGenerationRequest = request.getSceneGenerationRequest();
            log.info("Using provided sceneGenerationRequest: {}", sceneGenerationRequest);
        }
        
        // selectedPlanId 처리
        String selectedPlanId = null;
        if (request != null && request.getSelectedPlanId() != null && !request.getSelectedPlanId().trim().isEmpty()) {
            selectedPlanId = request.getSelectedPlanId();
            log.info("Using provided selectedPlanId: {}", selectedPlanId);
        } else {
            log.info("No selectedPlanId provided, will use default (A)");
        }
        
        List<SceneResponse> scenes = sceneService.generateScenes(projectId, selectedPlanId, sceneGenerationRequest);
        
        return ResponseEntity.ok(ApiResponse.success("씬 자동 생성 성공", scenes));
    }
    
    @Operation(summary = "씬 설계", description = "특정 씬에 대해 optional_elements, image_prompt, video_prompt를 생성합니다.")
    @PostMapping("/projects/{projectId}/scenes/{sceneId}/design")
    public ResponseEntity<ApiResponse<SceneResponse>> designScene(
            @Parameter(description = "프로젝트 ID") @PathVariable Long projectId,
            @Parameter(description = "씬 ID") @PathVariable Long sceneId,
            @Valid @RequestBody SceneDesignRequest request,
            Authentication authentication) {
        
        log.info("Designing scene: {} for project: {}, request: {}", sceneId, projectId, request.getDesignRequest());
        
        String loginId = resolveLoginId(authentication);
        SceneResponse scene = sceneService.designScene(projectId, sceneId, loginId, request);
        
        return ResponseEntity.ok(ApiResponse.success("씬 설계 성공", scene));
    }
    
    @Operation(summary = "씬 목록 조회", description = "프로젝트별 씬 목록을 조회합니다.")
    @GetMapping("/projects/{projectId}/scenes")
    public ResponseEntity<ApiResponse<List<SceneResponse>>> getScenesByProjectId(
            @Parameter(description = "프로젝트 ID") @PathVariable Long projectId,
            Authentication authentication) {
        
        log.info("Getting scenes for projectId: {}", projectId);
        
        String loginId = resolveLoginId(authentication);
        List<SceneResponse> scenes = sceneService.getScenesByProjectId(projectId);
        
        return ResponseEntity.ok(ApiResponse.success("Scene 목록 조회 성공", scenes));
    }
    
    @Operation(summary = "씬 설계 재추천", description = "특정 씬의 설계를 새로운 버전으로 재추천합니다.")
    @PostMapping("/projects/{projectId}/scenes/{sceneId}/design/regenerate")
    public ResponseEntity<ApiResponse<SceneDesignResponse>> regenerateSceneDesign(
            @Parameter(description = "프로젝트 ID") @PathVariable Long projectId,
            @Parameter(description = "씬 ID") @PathVariable Long sceneId,
            @RequestBody(required = false) SceneDesignRegenerateRequest request,
            Authentication authentication) {
        
        log.info("Regenerating design for scene: {} in project: {}", sceneId, projectId);
        
        String loginId = resolveLoginId(authentication);
        SceneDesignResponse response = sceneService.regenerateSceneDesign(projectId, sceneId, loginId, request);
        
        return ResponseEntity.ok(ApiResponse.success("씬 설계 재추천 성공", response));
    }
    
    @Operation(summary = "씬 설계 조회", description = "특정 씬의 설계 결과를 조회합니다.")
    @GetMapping("/projects/{projectId}/scenes/{sceneId}/design")
    public ResponseEntity<ApiResponse<SceneDesignResponse>> getSceneDesign(
            @Parameter(description = "프로젝트 ID") @PathVariable Long projectId,
            @Parameter(description = "씬 ID") @PathVariable Long sceneId,
            Authentication authentication) {
        
        log.info("Getting design for scene: {} in project: {}", sceneId, projectId);
        
        String loginId = resolveLoginId(authentication);
        SceneDesignResponse response = sceneService.getSceneDesign(projectId, sceneId, loginId);
        
        return ResponseEntity.ok(ApiResponse.success("씬 설계 조회 성공", response));
    }
    
    @Operation(summary = "씬 삭제", description = "특정 씬을 삭제합니다. 관련된 이미지와 영상도 함께 삭제됩니다.")
    @DeleteMapping("/projects/{projectId}/scenes/{sceneId}")
    public ResponseEntity<ApiResponse<Object>> deleteScene(
            @Parameter(description = "프로젝트 ID") @PathVariable Long projectId,
            @Parameter(description = "씬 ID") @PathVariable Long sceneId,
            Authentication authentication) {
        
        log.info("Deleting scene: {} for project: {}", sceneId, projectId);
        
        String loginId = resolveLoginId(authentication);
        sceneService.deleteScene(projectId, sceneId, loginId);
        
        return ResponseEntity.ok(ApiResponse.success("Scene 삭제 완료", java.util.Map.of("deletedSceneId", sceneId)));
    }
}
