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

    private final ProjectPlanRepository planRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final OpenAIService openAIService;
    private final ObjectMapper objectMapper;

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

        // OpenAI로 기획 생성
        log.info("OpenAI 기획 생성 시작 - 사용자 입력: {}", request.getUserPrompt());
        
        try {
            // OpenAI 호출
            String aiResponse = openAIService.generatePlan(request.getUserPrompt());
            log.info("OpenAI 응답 수신: {}", aiResponse);
            
            // JSON 추출
            String json = JsonUtils.extractJsonSafely(aiResponse);
            log.info("추출된 JSON: {}", json);
            
            // JSON 파싱
            PlanGenerationResponse planResponse = objectMapper.readValue(json, PlanGenerationResponse.class);
            log.info("기획 파싱 완료 - 제목: {}", planResponse.getTitle());
            
            // ProjectPlanResponse로 변환
            ProjectPlanResponse response = ProjectPlanResponse.builder()
                    .title(planResponse.getTitle())
                    .theme(planResponse.getCoreElements().getBackground())
                    .mainCharacter(planResponse.getCoreElements().getMainCharacter())
                    .background(planResponse.getCoreElements().getBackground())
                    .timeOfDay("전체")
                    .mood(planResponse.getCoreElements().getMood())
                    .style(planResponse.getCoreElements().getStyle())
                    .scenes(List.of()) // 기획 단계에서는 씬 목록 비워둠
                    .build();
            
            log.info("기획 생성 완료 - 제목: {}", response.getTitle());
            
            // 기획 저장
            ProjectPlan plan = ProjectPlan.builder()
                    .project(project)
                    .version(nextVersion)
                    .planData(response.toJson())
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
            
        } catch (Exception e) {
            log.error("기획 생성 실패 - fallback 실행", e);
            
            // fallback: userPrompt 기반 간단 생성
            ProjectPlanResponse response = ProjectPlanResponse.builder()
                    .title(request.getUserPrompt().length() > 50 ? 
                            request.getUserPrompt().substring(0, 47) + "..." : 
                            request.getUserPrompt())
                    .theme("기본 테마")
                    .mainCharacter("주인공")
                    .background("일반적인 배경")
                    .timeOfDay("전체")
                    .mood("중립")
                    .style("표준")
                    .scenes(List.of()) // 기획 단계에서는 씬 목록 비워둠
                    .build();
            
            log.info("Fallback 기획 생성 완료 - 제목: {}", response.getTitle());
            
            // 기획 저장
            ProjectPlan plan = ProjectPlan.builder()
                    .project(project)
                    .version(nextVersion)
                    .planData(response.toJson())
                    .userPrompt(request.getUserPrompt())
                    .status(ProjectPlan.PlanStatus.DRAFT)
                    .build();

            ProjectPlan savedPlan = planRepository.save(plan);
            
            // 프로젝트 상태 업데이트
            project.setStatus(ProjectStatus.PLANNING);
            projectRepository.save(project);

            log.info("Fallback 기획 저장 완료 - 프로젝트 ID: {}, 기획 ID: {}, 버전: {}", 
                    projectId, savedPlan.getId(), nextVersion);

            return savedPlan;
        }
    }

    public Optional<ProjectPlan> getLatestPlan(Long projectId) {
        return planRepository.findFirstByProjectIdOrderByCreatedAtDesc(projectId);
    }

    public List<ProjectPlan> getPlanHistory(Long projectId) {
        return planRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
    }
}
