package com.example.hdb.controller;

import com.example.hdb.dto.request.PlanCreateRequest;
import com.example.hdb.dto.response.ApiResponse;
import com.example.hdb.dto.response.ProjectPlanResponse;
import com.example.hdb.dto.response.ProjectResponse;
import com.example.hdb.entity.ProjectPlan;
import com.example.hdb.entity.ProjectStatus;
import com.example.hdb.service.PlanningService;
import com.example.hdb.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@Slf4j
@SecurityRequirement(name = "bearerAuth")
public class ProjectController extends BaseController {

    private final ProjectService projectService;
    private final PlanningService planningService;

    @Operation(summary = "프로젝트 생성", description = "새로운 프로젝트를 생성합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(
            @Valid @RequestBody com.example.hdb.dto.request.ProjectCreateRequest request,
            Authentication authentication) {
        
        String loginId = resolveLoginId(authentication);
        log.info("프로젝트 생성 요청 - 사용자: {}, 제목: {}", loginId, request.getTitle());
        
        // Entity 생성 후 DTO 변환
        var project = projectService.createProjectByLoginId(loginId, request);
        
        return ResponseEntity.ok(ApiResponse.success(ProjectResponse.from(project)));
    }

    @Operation(summary = "사용자 프로젝트 목록", description = "로그인한 사용자의 모든 프로젝트를 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> getUserProjects(Authentication authentication) {
        
        log.info("authentication = {}", authentication);
        String loginId = resolveLoginId(authentication);
        log.info("resolved loginId = {}", loginId);
        
        // Entity 목록 생성 후 DTO 변환
        var projects = projectService.getUserProjects(loginId);
        var responses = projects.stream()
                .map(ProjectResponse::from)
                .toList();
        
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @Operation(summary = "프로젝트 삭제", description = "특정 프로젝트와 관련된 모든 데이터를 삭제합니다.")
    @DeleteMapping("/{projectId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteProject(
            @Parameter(description = "프로젝트 ID") @PathVariable Long projectId,
            Authentication authentication) {
        
        String loginId = resolveLoginId(authentication);
        log.info("프로젝트 삭제 요청 - 사용자: {}, 프로젝트: {}", loginId, projectId);
        
        projectService.deleteProject(projectId, loginId);
        
        return ResponseEntity.ok(
                ApiResponse.success("프로젝트 삭제 완료",
                        Map.of("deletedProjectId", projectId))
        );
    }

    @Operation(summary = "프로젝트 상세 조회", description = "특정 프로젝트의 상세 정보를 조회합니다.")
    @GetMapping("/{projectId}")
    public ResponseEntity<ApiResponse<ProjectResponse>> getProject(
            @Parameter(description = "프로젝트 ID") @PathVariable Long projectId,
            Authentication authentication) {
        
        String loginId = resolveLoginId(authentication);
        log.info("프로젝트 상세 조회 - 사용자: {}, 프로젝트: {}", loginId, projectId);
        
        // Entity 조회 후 DTO 변환
        var project = projectService.getProjectByLoginId(loginId, projectId);
        
        return ResponseEntity.ok(ApiResponse.success(ProjectResponse.from(project)));
    }

    @Operation(summary = "기획 생성", description = "프로젝트의 기획을 생성합니다. OpenAI GPT를 사용합니다.")
    @PostMapping("/{projectId}/plans")
    public ResponseEntity<ApiResponse<ProjectPlanResponse>> createPlan(
            @Parameter(description = "프로젝트 ID") @PathVariable Long projectId,
            @Valid @RequestBody PlanCreateRequest request,
            Authentication authentication) {
        
        String loginId = resolveLoginId(authentication);
        log.info("기획 생성 요청 - 사용자: {}, 프로젝트: {}, 프롬프트: {}", 
                loginId, projectId, request.getUserPrompt());
        
        var plan = planningService.createPlan(loginId, projectId, request);
        
        // 기획 응답 변환
        ProjectPlanResponse response = convertToPlanResponse(plan);
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "기획 이력 조회", description = "프로젝트의 모든 기획 이력을 조회합니다.")
    @GetMapping("/{projectId}/plans")
    public ResponseEntity<ApiResponse<List<ProjectPlanResponse>>> getPlanHistory(
            @Parameter(description = "프로젝트 ID") @PathVariable Long projectId,
            Authentication authentication) {
        
        String loginId = resolveLoginId(authentication);
        log.info("기획 이력 조회 - 사용자: {}, 프로젝트: {}", loginId, projectId);
        
        var plans = planningService.getPlanHistory(projectId);
        var responses = plans.stream()
                .map(this::convertToPlanResponse)
                .toList();
        
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    private ProjectPlanResponse convertToPlanResponse(ProjectPlan plan) {
        try {
            // JSON 데이터를 객체로 변환
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(plan.getPlanData(), ProjectPlanResponse.class);
        } catch (Exception e) {
            log.error("기획 데이터 변환 실패", e);
            return ProjectPlanResponse.builder().build();
        }
    }
}
