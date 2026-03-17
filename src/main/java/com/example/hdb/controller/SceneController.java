package com.example.hdb.controller;

import com.example.hdb.dto.common.ApiResponse;
import com.example.hdb.dto.request.SceneCreateRequest;
import com.example.hdb.dto.request.SceneUpdateRequest;
import com.example.hdb.dto.response.SceneResponse;
import com.example.hdb.service.SceneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/scenes")
@RequiredArgsConstructor
@Slf4j
@Validated
public class SceneController {
    
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
}
