package com.example.hdb.controller;

import com.example.hdb.dto.request.PlanCreateRequest;
import com.example.hdb.dto.request.PlanSelectRequest;
import com.example.hdb.dto.response.ApiResponse;
import com.example.hdb.dto.response.ProjectPlanResponse;
import com.example.hdb.dto.response.ProjectPlanResponseV2;
import com.example.hdb.dto.response.PlanSelectResponse;
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
import java.util.Optional;
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

    @Operation(summary = "기획 생성", description = "프로젝트를 기반으로 3가지 기획안을 생성합니다.")
    @PostMapping("/{projectId}/plans")
    public ResponseEntity<ApiResponse<ProjectPlanResponseV2>> createPlan(
            @Parameter(description = "프로젝트 ID") @PathVariable Long projectId,
            @Valid @RequestBody PlanCreateRequest request,
            Authentication authentication) {
        
        String loginId = resolveLoginId(authentication);
        log.info("기획 생성 요청 - 사용자: {}, 프로젝트: {}, 프롬프트: {}", 
                loginId, projectId, request.getUserPrompt());
        
        var plan = planningService.createPlan(loginId, projectId, request);
        
        // 기획 응답 변환 (V2 구조)
        ProjectPlanResponseV2 response = convertToPlanResponseV2(plan);
        
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

    @Operation(summary = "기획안 선택", description = "사용자가 선택한 기획안을 프로젝트의 최종 선택 기획안으로 저장합니다.")
    @PostMapping("/{projectId}/plans/{planId}/select")
    public ResponseEntity<ApiResponse<PlanSelectResponse>> selectPlan(
            @Parameter(description = "프로젝트 ID") @PathVariable Long projectId,
            @Parameter(description = "기획안 ID (1/2/3)") @PathVariable Integer planId,
            Authentication authentication) {
        
        String loginId = resolveLoginId(authentication);
        log.info("기획안 선택 - 사용자: {}, 프로젝트: {}, 기획안: {}", loginId, projectId, planId);
        
        // 기획안 선택 처리
        planningService.selectPlan(projectId, planId);
        
        // 선택된 기획안 정보 조회
        Optional<ProjectPlan> latestPlan = planningService.getLatestPlan(projectId);
        if (latestPlan.isPresent()) {
            ProjectPlanResponseV2 planResponse = convertToPlanResponseV2(latestPlan.get());
            ProjectPlanResponseV2.Plan selectedPlan = planResponse.getPlans().stream()
                    .filter(plan -> planId.equals(plan.getPlanId()))
                    .findFirst()
                    .orElse(planResponse.getPlans().get(0)); // fallback
            
            PlanSelectResponse response = PlanSelectResponse.builder()
                    .selectedPlanId(selectedPlan.getPlanId())
                    .selectedTitle(selectedPlan.getTitle())
                    .selectedFocus(selectedPlan.getFocus())
                    .build();
            
            return ResponseEntity.ok(ApiResponse.success(response));
        }
        
        throw new RuntimeException("기획안을 찾을 수 없습니다.");
    }

    private ProjectPlanResponseV2 convertToPlanResponseV2(ProjectPlan plan) {
        try {
            String planData = plan.getPlanData();
            log.info("=== Converting Plan Data V2 ===");
            log.info("Plan ID: {}", plan.getId());
            log.info("Stored plan_data JSON: {}", planData);
            
            // ProjectPlanResponseV2.fromJson() 사용하여 역직렬화
            ProjectPlanResponseV2 response = ProjectPlanResponseV2.fromJson(planData);
            
            log.info("Converted plan response V2 - plans count: {}", 
                    response.getPlans() != null ? response.getPlans().size() : 0);
            
            return response;
            
        } catch (Exception e) {
            log.error("Failed to convert plan to V2 response, using fallback", e);
            return ProjectPlanResponseV2.createFallbackResponse();
        }
    }

    private ProjectPlanResponse convertToPlanResponse(ProjectPlan plan) {
        try {
            String planData = plan.getPlanData();
            log.info("=== Converting Plan Data ===");
            log.info("Plan ID: {}", plan.getId());
            log.info("Stored plan_data JSON: {}", planData);
            
            // ProjectPlanResponse.fromJson() 사용하여 역직렬화
            ProjectPlanResponse response = ProjectPlanResponse.fromJson(planData);
            
            // createdAt 설정
            if (response.getCreatedAt() == null) {
                response.setCreatedAt(plan.getCreatedAt());
            }
            
            log.info("Converted plan response - displayText: {}", response.getDisplayText());
            log.info("Converted plan response - coreElements: {}", response.getCoreElements());
            log.info("Converted plan response - meta.storyOptions size: {}", 
                    response.getMeta() != null && response.getMeta().getStoryOptions() != null ? 
                    response.getMeta().getStoryOptions().size() : 0);
            
            return response;
            
        } catch (Exception e) {
            log.error("기획 데이터 변환 실패, using fallback", e);
            // fallback 응답 생성
            ProjectPlanResponse fallbackResponse = ProjectPlanResponse.createFallbackResponse();
            fallbackResponse.setCreatedAt(plan.getCreatedAt());
            return fallbackResponse;
        }
    }
}
