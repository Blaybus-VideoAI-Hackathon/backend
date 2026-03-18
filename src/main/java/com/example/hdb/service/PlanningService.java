package com.example.hdb.service;

import com.example.hdb.dto.request.PlanCreateRequest;
import com.example.hdb.dto.response.ProjectPlanResponse;
import com.example.hdb.entity.ProjectPlan;
import com.example.hdb.entity.ProjectStatus;
import com.example.hdb.exception.BusinessException;
import com.example.hdb.exception.ErrorCode;
import com.example.hdb.repository.ProjectPlanRepository;
import com.example.hdb.repository.ProjectRepository;
import com.example.hdb.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PlanningService {

    private final ProjectPlanRepository planRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    @Value("${openai.api.key}")
    private String apiKey;

    public ProjectPlan createPlan(String loginId, Long projectId, PlanCreateRequest request) {
        // 프로젝트 존재 확인
        var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        // 사용자 권한 확인
        if (!project.getUser().getLoginId().equals(loginId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }

        // 버전 계산
        int nextVersion = planRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream()
                .mapToInt(ProjectPlan::getVersion)
                .max()
                .orElse(0) + 1;

        // OpenAI로 기획 생성 (임시로 하드코딩)
        log.info("OpenAI 기획 생성 시작 - 사용자 입력: {}", request.getUserPrompt());
        
        // 임시 응답 (실제 OpenAI 연동 전)
        ProjectPlanResponse planResponse = ProjectPlanResponse.builder()
                .title("AI 생성 제목")
                .theme("AI 생성 주제")
                .mainCharacter("AI 생성 주인공")
                .background("AI 생성 배경")
                .timeOfDay("아침")
                .mood("밝음")
                .style("애니메이션")
                .scenes(List.of(
                        ProjectPlanResponse.SceneInfo.builder()
                                .sceneNumber(1)
                                .description("첫 번째 씬")
                                .imagePrompt("첫 번째 씬 이미지")
                                .build(),
                        ProjectPlanResponse.SceneInfo.builder()
                                .sceneNumber(2)
                                .description("두 번째 씬")
                                .imagePrompt("두 번째 씬 이미지")
                                .build()
                ))
                .build();
        
        log.info("기획 생성 완료 - 생성된 씬 수: {}", 
                planResponse.getScenes() != null ? planResponse.getScenes().size() : 0);

        // 기획 저장
        ProjectPlan plan = ProjectPlan.builder()
                .project(project)
                .version(nextVersion)
                .planData(planResponse.toJson())
                .userPrompt(request.getUserPrompt())
                .status(ProjectPlan.PlanStatus.DRAFT)
                .build();

        ProjectPlan savedPlan = planRepository.save(plan);
        
        // 프로젝트 상태 업데이트
        project.setStatus(ProjectStatus.PLANNING);
        projectRepository.save(project);

        log.info("기획 저장 완료 - 프로젝트 ID: {}, 기획 ID: {}, 버전: {}", 
                projectId, savedPlan.getId(), nextVersion);

        return savedPlan;
    }

    public Optional<ProjectPlan> getLatestPlan(Long projectId) {
        return planRepository.findFirstByProjectIdOrderByCreatedAtDesc(projectId);
    }

    public List<ProjectPlan> getPlanHistory(Long projectId) {
        return planRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
    }
}
