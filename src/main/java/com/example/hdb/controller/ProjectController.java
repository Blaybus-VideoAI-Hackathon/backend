package com.example.hdb.controller;

import com.example.hdb.dto.request.PlanCreateRequest;
import com.example.hdb.dto.request.PlanSelectRequest;
import com.example.hdb.dto.request.PlanningGenerateRequest;
import com.example.hdb.dto.response.ApiResponse;
import com.example.hdb.dto.response.ProjectPlanResponse;
import com.example.hdb.dto.response.ProjectPlanResponseV3;
import com.example.hdb.dto.response.PlanSelectResponse;
import com.example.hdb.dto.response.PlanningGenerateResponse;
import com.example.hdb.dto.response.PlanningSummaryResponse;
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

    @Operation(summary = "기획 생성", description = "사용자의 아이디어를 바탕으로 3개의 차별화된 기획안을 생성합니다.")
    @PostMapping("/{projectId}/plans")
    public ResponseEntity<ApiResponse<ProjectPlanResponseV3>> createPlan(
            @Parameter(description = "프로젝트 ID") @PathVariable Long projectId,
            @Valid @RequestBody PlanCreateRequest request,
            Authentication authentication) {
        
        String loginId = resolveLoginId(authentication);
        log.info("기획 생성 요청 - 사용자: {}, 프로젝트: {}, 아이디어: {}", loginId, projectId, request.getUserPrompt());
        
        // 기획 생성 서비스 호출
        var plan = planningService.createPlan(loginId, projectId, request);
        
        // 응답 변환
        var response = convertToPlanResponseV3(plan);
        
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
            ProjectPlanResponseV3 planResponse = convertToPlanResponseV3(latestPlan.get());
            ProjectPlanResponseV3.Plan selectedPlan = planResponse.getPlans().stream()
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

    @Operation(summary = "프로젝트 기획 생성", description = "사용자 프롬프트를 기반으로 실제 LLM을 호출하여 기획을 생성하고 핵심요소와 기획안을 한 번에 반환합니다.")
    @PostMapping("/{projectId}/planning/generate")
    public ResponseEntity<ApiResponse<PlanningGenerateResponse>> generatePlanning(
            @Parameter(description = "프로젝트 ID") @PathVariable Long projectId,
            @Valid @RequestBody PlanningGenerateRequest request,
            Authentication authentication) {
        
        String loginId = resolveLoginId(authentication);
        log.info("기획 생성 - 사용자: {}, 프로젝트: {}, 프롬프트: {}", loginId, projectId, request.getUserPrompt());
        
        // 기획 생성 서비스 호출
        var planning = planningService.generatePlanning(projectId, request.getUserPrompt(), loginId);
        
        return ResponseEntity.ok(ApiResponse.success("기획 생성 성공", planning));
    }

    @Operation(summary = "프로젝트 기획 요약 조회", description = "선택된 기획안 기준의 핵심 요소와 스토리라인을 한 번에 조회합니다.")
    @GetMapping("/{projectId}/planning-summary")
    public ResponseEntity<ApiResponse<PlanningSummaryResponse>> getPlanningSummary(
            @Parameter(description = "프로젝트 ID") @PathVariable Long projectId,
            Authentication authentication) {
        
        String loginId = resolveLoginId(authentication);
        log.info("기획 요약 조회 - 사용자: {}, 프로젝트: {}", loginId, projectId);
        
        // 기획 요약 서비스 호출
        var summary = planningService.getPlanningSummary(projectId);
        
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    private ProjectPlanResponseV3 convertToPlanResponseV3(ProjectPlan plan) {
        try {
            String planData = plan.getPlanData();
            log.info("=== Converting Plan Data V3 ===");
            log.info("Plan ID: {}", plan.getId());
            log.info("Stored plan_data JSON: {}", planData);
            
            // ProjectPlanResponseV3.fromJson() 사용하여 역직렬화
            ProjectPlanResponseV3 response = ProjectPlanResponseV3.fromJson(planData);
            
            log.info("Converted plans count: {}", response.getPlans() != null ? response.getPlans().size() : 0);
            return response;
            
        } catch (Exception e) {
            log.error("Failed to convert plan to V3 response", e);
            throw new RuntimeException("기획 데이터 변환에 실패했습니다.", e);
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
