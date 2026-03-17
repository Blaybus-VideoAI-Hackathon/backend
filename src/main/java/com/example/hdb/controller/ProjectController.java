package com.example.hdb.controller;

import com.example.hdb.dto.common.ApiResponse;
import com.example.hdb.dto.request.CoreElementsRequest;
import com.example.hdb.dto.request.IdeaGenerationRequest;
import com.example.hdb.dto.request.ProjectCreateRequest;
import com.example.hdb.dto.response.IdeaGenerationResponse;
import com.example.hdb.dto.response.ProjectResponse;
import com.example.hdb.dto.response.SceneResponse;
import com.example.hdb.dto.response.SceneGenerationResponse;
import com.example.hdb.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@Slf4j
@Validated
public class ProjectController {
    
    private final ProjectService projectService;
    
    @PostMapping
    @Operation(summary = "프로젝트 생성", description = "새로운 프로젝트를 생성합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "프로젝트 생성 성공")
    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(
            @Valid @RequestBody ProjectCreateRequest request) {
        
        log.info("Creating project: {}", request.getTitle());
        ProjectResponse response = projectService.createProject(request);
        
        return ResponseEntity.status(201)
                .body(ApiResponse.success("프로젝트 생성 성공", response));
    }
    
    @GetMapping
    @Operation(summary = "프로젝트 목록 조회", description = "사용자 ID로 프로젝트 목록을 조회합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "프로젝트 목록 조회 성공")
    public ResponseEntity<ApiResponse<java.util.List<ProjectResponse>>> getProjectsByUserId(
            @Parameter(description = "사용자 ID", required = true)
            @RequestParam Long userId) {
        
        log.info("Getting projects for userId: {}", userId);
        var projects = projectService.getProjectsByUserId(userId);
        
        return ResponseEntity.ok()
                .body(ApiResponse.success("프로젝트 목록 조회 성공", projects));
    }
    
    @GetMapping("/{projectId}")
    @Operation(summary = "프로젝트 상세 조회", description = "프로젝트 ID로 상세 정보를 조회합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "프로젝트 상세 조회 성공")
    public ResponseEntity<ApiResponse<ProjectResponse>> getProjectById(
            @Parameter(description = "프로젝트 ID", required = true)
            @PathVariable Long projectId) {
        
        log.info("Getting project by id: {}", projectId);
        ProjectResponse response = projectService.getProjectById(projectId);
        
        return ResponseEntity.ok()
                .body(ApiResponse.success("프로젝트 상세 조회 성공", response));
    }
    
    @GetMapping("/{projectId}/secure")
    @Operation(summary = "프로젝트 상세 조회 (권한 검증)", description = "프로젝트 ID와 사용자 ID로 상세 정보를 조회합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "프로젝트 상세 조회 성공")
    public ResponseEntity<ApiResponse<ProjectResponse>> getProjectByIdAndUserId(
            @Parameter(description = "프로젝트 ID", required = true)
            @PathVariable Long projectId,
            @Parameter(description = "사용자 ID", required = true)
            @RequestParam Long userId) {
        
        log.info("Getting project by id: {} for userId: {}", projectId, userId);
        ProjectResponse response = projectService.getProjectByIdAndUserId(projectId, userId);
        
        return ResponseEntity.ok()
                .body(ApiResponse.success("프로젝트 상세 조회 성공", response));
    }
    
    @GetMapping("/{projectId}/scenes")
    @Operation(summary = "프로젝트별 씬 목록 조회", description = "프로젝트 ID로 속한 씬 목록을 조회합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "씬 목록 조회 성공")
    public ResponseEntity<ApiResponse<java.util.List<SceneResponse>>> getScenesByProjectId(
            @Parameter(description = "프로젝트 ID", required = true)
            @PathVariable Long projectId) {
        
        log.info("Getting scenes for projectId: {}", projectId);
        var scenes = projectService.getScenesByProjectId(projectId);
        
        return ResponseEntity.ok()
                .body(ApiResponse.success("씬 목록 조회 성공", scenes));
    }
    
    @GetMapping("/{projectId}/scenes/secure")
    @Operation(summary = "프로젝트별 씬 목록 조회 (권한 검증)", description = "프로젝트 ID와 사용자 ID로 속한 씬 목록을 조회합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "씬 목록 조회 성공")
    public ResponseEntity<ApiResponse<java.util.List<SceneResponse>>> getScenesByProjectIdAndUserId(
            @Parameter(description = "프로젝트 ID", required = true)
            @PathVariable Long projectId,
            @Parameter(description = "사용자 ID", required = true)
            @RequestParam Long userId) {
        
        log.info("Getting scenes for projectId: {} and userId: {}", projectId, userId);
        var scenes = projectService.getScenesByProjectIdAndUserId(projectId, userId);
        
        return ResponseEntity.ok()
                .body(ApiResponse.success("씬 목록 조회 성공", scenes));
    }
    
    @PatchMapping("/{projectId}/core-elements")
    @Operation(summary = "핵심 요소 저장", description = "프로젝트의 핵심 요소를 저장하거나 수정합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "핵심 요소 저장 성공")
    public ResponseEntity<ApiResponse<ProjectResponse>> updateCoreElements(
            @Parameter(description = "프로젝트 ID", required = true)
            @PathVariable Long projectId,
            @Valid @RequestBody CoreElementsRequest request) {
        
        log.info("Updating core elements for projectId: {}", projectId);
        ProjectResponse response = projectService.updateCoreElements(projectId, request);
        
        return ResponseEntity.ok()
                .body(ApiResponse.success("핵심 요소 저장 성공", response));
    }
    
    @PostMapping("/{projectId}/idea")
    @Operation(summary = "프로젝트 아이디어 생성", description = "사용자 입력을 기반으로 프로젝트 아이디어를 생성합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "프로젝트 아이디어 생성 성공")
    public ResponseEntity<ApiResponse<IdeaGenerationResponse>> generateProjectIdea(
            @Parameter(description = "프로젝트 ID", required = true)
            @PathVariable Long projectId,
            @Valid @RequestBody IdeaGenerationRequest request) {
        
        log.info("Generating project idea for projectId: {}", projectId);
        ApiResponse<IdeaGenerationResponse> response = projectService.generateProjectIdea(projectId, request);
        
        return ResponseEntity.ok()
                .body(response);
    }
    
    @PostMapping("/{projectId}/scenes/generate")
    @Operation(summary = "프로젝트 씬 생성", description = "씬 아이디어를 기반으로 프로젝트 씬 목록을 생성합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "프로젝트 씬 생성 성공")
    public ResponseEntity<ApiResponse<SceneGenerationResponse>> generateProjectScenes(
            @Parameter(description = "프로젝트 ID", required = true)
            @PathVariable Long projectId,
            @Valid @RequestBody com.example.hdb.dto.request.SceneGenerationRequest request) {
        
        log.info("Generating project scenes for projectId: {}", projectId);
        ApiResponse<SceneGenerationResponse> response = projectService.generateProjectScenes(projectId, request);
        
        return ResponseEntity.ok()
                .body(response);
    }
}
