package com.example.hdb.service;

import com.example.hdb.dto.openai.PlanGenerationResponse;
import com.example.hdb.dto.request.PlanCreateRequest;
import com.example.hdb.dto.response.ProjectPlanResponse;
import com.example.hdb.entity.ProjectPlan;
import com.example.hdb.entity.ProjectStatus;
import com.example.hdb.exception.BusinessException;
import com.example.hdb.exception.ErrorCode;
import com.example.hdb.repository.ProjectPlanRepository;
import com.example.hdb.repository.ProjectRepository;
import com.example.hdb.repository.UserRepository;
import com.example.hdb.util.JsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class PlanningService {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PlanningService.class);
    
    private final ProjectPlanRepository projectPlanRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final OpenAIService openAIService;
    
    /**
     * 사용자가 선택한 기획안을 프로젝트의 최종 선택 기획안으로 저장
     */
    public void selectPlan(Long projectId, Integer selectedPlanId) {
        log.info("=== Plan Selection Started ===");
        log.info("Project ID: {}", projectId);
        log.info("Selected Plan ID: {}", selectedPlanId);
        
        // 최신 기획안 조회
        Optional<ProjectPlan> latestPlan = getLatestPlan(projectId);
        if (latestPlan.isEmpty()) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
        
        ProjectPlan plan = latestPlan.get();
        log.info("Found latest plan: {}", plan.getId());
        
        // 선택된 기획안 유효성 확인 (1/2/3)
        if (selectedPlanId < 1 || selectedPlanId > 3) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
        
        // TODO: Project 엔티티에 selectedPlanId 필드 추가 필요
        // 현재는 로그만 남기고 실제 저장은 추후 구현
        log.info("Plan selection completed - Project: {}, SelectedPlan: {}", projectId, selectedPlanId);
    }

    public ProjectPlan createPlan(String loginId, Long projectId, PlanCreateRequest request) {
        // 프로젝트 존재 확인
        var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        // 사용자 권한 확인
        if (!project.getUser().getLoginId().equals(loginId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }

        // 프로젝트 버전 계산
        int nextVersion = projectPlanRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream()
                .mapToInt(ProjectPlan::getVersion)
                .max()
                .orElse(0) + 1;

        // OpenAI로 기획 생성
        log.info("OpenAI 기획 생성 시작 - 사용자 입력: {}", request.getUserPrompt());
        
        try {
            // OpenAI 호출
            String aiResponse = openAIService.generatePlan(request.getUserPrompt());
            log.info("OpenAI 응답 수신: {}", aiResponse);
            
            // JSON 추출
            String json = JsonUtils.extractJsonSafely(aiResponse);
            log.info("=== Saving Plan Data ===");
            log.info("Extracted JSON: {}", json);
            
            // 기획 저장 (OpenAI 응답을 그대로 저장)
            ProjectPlan plan = ProjectPlan.builder()
                    .project(project)
                    .version(nextVersion)
                    .planData(json)  // OpenAI 응답 JSON을 그대로 저장
                    .userPrompt(request.getUserPrompt())
                    .status(ProjectPlan.PlanStatus.DRAFT)
                    .build();

            ProjectPlan savedPlan = projectPlanRepository.save(plan);
            
            // 프로젝트 상태 업데이트
            project.setStatus(ProjectStatus.PLANNING);
            projectRepository.save(project);

            log.info("기획 저장 완료 - 프로젝트 ID: {}, 기획 ID: {}, 버전: {}", 
                    projectId, savedPlan.getId(), nextVersion);

            return savedPlan;
            
        } catch (Exception e) {
            log.error("기획 생성 실패 - fallback 실행", e);
            
            // fallback: userPrompt 기반 3개 기획안 생성
            String fallbackJson = createFallbackPlanJson(request.getUserPrompt());
            log.info("=== Using Fallback Plan Data ===");
            log.info("Fallback JSON: {}", fallbackJson);
            
            // 기획 저장
            ProjectPlan plan = ProjectPlan.builder()
                    .project(project)
                    .version(nextVersion)
                    .planData(fallbackJson)  // fallback JSON 저장
                    .userPrompt(request.getUserPrompt())
                    .status(ProjectPlan.PlanStatus.DRAFT)
                    .build();

            ProjectPlan savedPlan = projectPlanRepository.save(plan);
            
            // 프로젝트 상태 업데이트
            project.setStatus(ProjectStatus.PLANNING);
            projectRepository.save(project);

            log.info("Fallback 기획 저장 완료 - 프로젝트 ID: {}, 기획 ID: {}, 버전: {}", 
                    projectId, savedPlan.getId(), nextVersion);

            return savedPlan;
        }
    }
    
    private String createFallbackPlanJson(String userPrompt) {
        return String.format("""
            {
              "displayText": "입력한 아이디어를 바탕으로 3가지 기획안을 제안합니다.",
              "coreElements": {
                "mainCharacter": "주요 인물",
                "background": "배경 설정",
                "style": "스타일",
                "ratio": "16:9",
                "purpose": "프로모션"
              },
              "meta": {
                "storyOptions": [
                  {
                    "id": "A",
                    "title": "기획안 A",
                    "description": "%s를 기반으로 한 제품 중심의 기획안입니다. 제품의 특징과 장점을 효과적으로 보여줍니다."
                  },
                  {
                    "id": "B",
                    "title": "기획안 B", 
                    "description": "%s를 기반으로 한 감성 중심의 기획안입니다. 감동적인 스토리와 감성적인 장면을 강조합니다."
                  },
                  {
                    "id": "C",
                    "title": "기획안 C",
                    "description": "%s를 기반으로 한 기능 시연 중심의 기획안입니다. 실제 사용 방법과 기능을 명확하게 보여줍니다."
                  }
                ]
              }
            }
            """, userPrompt, userPrompt, userPrompt);
    }

    public Optional<ProjectPlan> getLatestPlan(Long projectId) {
        return projectPlanRepository.findFirstByProjectIdOrderByCreatedAtDesc(projectId);
    }

    public List<ProjectPlan> getPlanHistory(Long projectId) {
        return projectPlanRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
    }
}
