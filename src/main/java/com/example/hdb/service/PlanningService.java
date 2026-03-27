package com.example.hdb.service;

import com.example.hdb.dto.request.PlanCreateRequest;
import com.example.hdb.dto.response.PlanAnalysisResponse;
import com.example.hdb.dto.response.PlanningGenerateResponse;
import com.example.hdb.dto.response.PlanningSummaryResponse;
import com.example.hdb.dto.response.PromptGenerateResponse;
import com.example.hdb.entity.ProjectPlan;
import com.example.hdb.entity.Scene;
import com.example.hdb.exception.BusinessException;
import com.example.hdb.exception.ErrorCode;
import com.example.hdb.repository.ProjectPlanRepository;
import com.example.hdb.repository.ProjectRepository;
import com.example.hdb.repository.SceneRepository;
import com.example.hdb.repository.UserRepository;
import com.example.hdb.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PlanningService {

    private final ProjectPlanRepository projectPlanRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final SceneRepository sceneRepository;
    private final ObjectMapper objectMapper;
    private final OpenAIService openAIService;

    // ──────────────────────────────────────────
    // 기획 생성 (generatePlanning - 재시도 로직 포함)
    // ──────────────────────────────────────────

    public PlanningGenerateResponse generatePlanning(Long projectId, String userPrompt, String loginId) {
        log.info("=== Planning Generation Started ===");
        log.info("projectId={}, loginId={}, userPrompt={}", projectId, loginId, userPrompt);

        var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        try {
            String aiResponse = openAIService.generatePlanningWithSummary(
                    userPrompt,
                    project.getPurpose(),
                    project.getDuration(),
                    project.getRatio(),
                    project.getStyle()
            );

            log.info("=== LLM RAW RESPONSE (PLANNING) ===");
            log.info("Raw AI Response: {}", aiResponse);

            String json = JsonUtils.extractJsonSafely(aiResponse);
            JsonNode root = objectMapper.readTree(json);
            JsonNode plansNode = root.path("plans");

            if (!plansNode.isArray() || plansNode.isEmpty()) {
                log.warn("Plans node is not array or empty, using fallback");
                throw new RuntimeException("Invalid plans structure");
            }

            List<PlanningGenerateResponse.Plan> plans = objectMapper.convertValue(
                    plansNode,
                    new TypeReference<List<PlanningGenerateResponse.Plan>>() {}
            );

            // ★★★ 3개가 아니면 재시도 ★★★
            if (plans.size() != 3) {
                log.warn("Expected 3 plans but got {}, retrying with strict format", plans.size());

                String stricterResponse = openAIService.generatePlanningWithStrictFormat(
                        userPrompt,
                        project.getPurpose(),
                        project.getDuration(),
                        project.getRatio(),
                        project.getStyle()
                );

                log.info("=== RETRY RAW RESPONSE ===");
                log.info("Retry Response: {}", stricterResponse);

                try {
                    String retryJson = JsonUtils.extractJsonSafely(stricterResponse);
                    JsonNode retryRoot = objectMapper.readTree(retryJson);
                    JsonNode retryPlansNode = retryRoot.path("plans");

                    if (retryPlansNode.isArray() && retryPlansNode.size() == 3) {
                        List<PlanningGenerateResponse.Plan> retryPlans = objectMapper.convertValue(
                                retryPlansNode,
                                new TypeReference<List<PlanningGenerateResponse.Plan>>() {}
                        );
                        log.info("=== RETRY SUCCESS: Got 3 plans ===");
                        return PlanningGenerateResponse.builder()
                                .projectId(projectId)
                                .selectedPlanId(project.getSelectedPlanId())
                                .plans(retryPlans)
                                .build();
                    }
                } catch (Exception retryE) {
                    log.warn("Retry failed, using fallback: {}", retryE.getMessage());
                }

                throw new RuntimeException("Expected 3 plans but got " + plans.size());
            }

            log.info("=== PARSING RESULT (PLANNING) ===");
            log.info("=== FALLBACK STATUS: false (LLM response used) ===");

            return PlanningGenerateResponse.builder()
                    .projectId(projectId)
                    .selectedPlanId(project.getSelectedPlanId())
                    .plans(plans)
                    .build();

        } catch (Exception e) {
            log.error("기획 생성 실패 - LLM 응답 파싱 오류, Fallback 사용: {}", e.getMessage());
            log.info("=== FALLBACK STATUS: true (LLM response parsing failed) ===");

            List<PlanningGenerateResponse.Plan> fallbackPlans = buildDynamicFallbackPlans(
                    userPrompt,
                    project.getPurpose(),
                    project.getDuration(),
                    project.getRatio(),
                    project.getStyle()
            );

            log.info("=== FALLBACK PLANS GENERATED ===");
            log.info("Generated {} fallback plans", fallbackPlans.size());

            return PlanningGenerateResponse.builder()
                    .projectId(projectId)
                    .selectedPlanId(project.getSelectedPlanId())
                    .plans(fallbackPlans)
                    .build();
        }
    }

    // ──────────────────────────────────────────
    // ProjectController에서 호출하는 메서드들
    // ──────────────────────────────────────────

    /**
     * 기획 생성 (createPlan)
     */
    public ProjectPlan createPlan(String loginId, Long projectId, PlanCreateRequest request) {
        var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        ProjectPlan plan = ProjectPlan.builder()
                .project(project)
                .version(1)
                .planData("")
                .userPrompt(request.getUserPrompt())
                .status(ProjectPlan.PlanStatus.DRAFT)
                .build();

        return projectPlanRepository.save(plan);
    }

    /**
     * 최신 기획 조회 (getLatestPlan - public)
     */
    public Optional<ProjectPlan> getLatestPlan(Long projectId) {
        return projectPlanRepository.findFirstByProjectIdOrderByCreatedAtDesc(projectId);
    }

    /**
     * 기획 이력 조회
     */
    public List<ProjectPlan> getPlanHistory(Long projectId) {
        return projectPlanRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    /**
     * 기획안 분석
     */
    public PlanAnalysisResponse analyzeSelectedPlan(Long projectId, Integer planId, String loginId) {
        var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        validateProjectOwnership(project, loginId);

        return buildDynamicFallbackAnalysis(projectId, planId, null, project);
    }

    /**
     * 최신 기획 분석 조회
     */
    public PlanAnalysisResponse getLatestPlanAnalysis(Long projectId) {
        var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        Optional<ProjectPlan> latestPlan = getLatestPlan(projectId);
        if (latestPlan.isEmpty()) {
            return buildDynamicFallbackAnalysis(projectId, 1, null, project);
        }

        ProjectPlan plan = latestPlan.get();
        if (plan.getAnalysisData() != null && !plan.getAnalysisData().isEmpty()) {
            try {
                return objectMapper.readValue(plan.getAnalysisData(), PlanAnalysisResponse.class);
            } catch (Exception e) {
                log.warn("Failed to parse stored analysis data, returning fallback", e);
            }
        }

        return buildDynamicFallbackAnalysis(projectId, 1, null, project);
    }

    /**
     * 최신 기획 조회 (getLatestPlanning)
     */
    public PlanningGenerateResponse getLatestPlanning(Long projectId, String loginId) {
        var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        validateProjectOwnership(project, loginId);

        return PlanningGenerateResponse.builder()
                .projectId(projectId)
                .selectedPlanId(project.getSelectedPlanId())
                .plans(new java.util.ArrayList<>())
                .build();
    }

    /**
     * 프롬프트 생성
     */
    public PromptGenerateResponse generatePrompt(Long projectId, Long sceneId, String loginId) {
        var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        validateProjectOwnership(project, loginId);

        return PromptGenerateResponse.builder()
                .imagePrompt("생성된 이미지 프롬프트")
                .videoPrompt("생성된 비디오 프롬프트")
                .build();
    }

    // ──────────────────────────────────────────
    // 기획안 선택
    // ──────────────────────────────────────────

    public void selectPlan(Long projectId, Integer selectedPlanId) {
        log.info("=== Plan Selection Started === projectId={}, selectedPlanId={}", projectId, selectedPlanId);

        Optional<ProjectPlan> latestPlan = getLatestPlan(projectId);
        if (latestPlan.isEmpty()) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }

        if (selectedPlanId < 1 || selectedPlanId > 3) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }

        var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        project.setSelectedPlanId(selectedPlanId);
        projectRepository.save(project);

        log.info("Plan selection completed - projectId={}, selectedPlanId={}", projectId, selectedPlanId);
    }

    // ──────────────────────────────────────────
    // 기획 요약 조회
    // ──────────────────────────────────────────

    public PlanningSummaryResponse getPlanningSummary(Long projectId) {
        log.info("=== Planning Summary Started === projectId={}", projectId);

        var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        Optional<ProjectPlan> latestPlan = getLatestPlan(projectId);
        if (latestPlan.isEmpty()) {
            return createDefaultPlanningSummary(projectId, project);
        }

        return createDefaultPlanningSummary(projectId, project);
    }

    // ──────────────────────────────────────────
    // Fallback 기획 생성
    // ──────────────────────────────────────────

    private List<PlanningGenerateResponse.Plan> buildDynamicFallbackPlans(
            String userPrompt,
            String purpose,
            Integer duration,
            String ratio,
            String style) {

        String mainChar = extractMainCharacter(userPrompt);
        String background = extractBackground(userPrompt);
        String situation = extractSituation(userPrompt);

        return java.util.List.of(
                // 감성형
                PlanningGenerateResponse.Plan.builder()
                        .planId(1)
                        .title("감성 버전: " + mainChar + "의 따뜻한 일상")
                        .focus("감정이입과 공감")
                        .displayText(mainChar + "의 일상")
                        .recommendationReason("따뜻한 감성")
                        .strengths(java.util.List.of("감정", "공감", "기억"))
                        .targetUseCase("감성 브랜드")
                        .storyLine(mainChar + "이(가) " + background + "에서 " + situation + "한다.")
                        .coreElements(PlanningGenerateResponse.CoreElements.builder()
                                .purpose(purpose)
                                .duration(duration)
                                .ratio(ratio)
                                .style(style)
                                .mainCharacter(mainChar)
                                .subCharacters(java.util.List.of(background))
                                .backgroundWorld(background)
                                .storyFlow("도입 → 전개 → 감정 고조 → 마무리")
                                .storyLine(mainChar + "의 이야기")
                                .build())
                        .build(),
                // 액션형
                PlanningGenerateResponse.Plan.builder()
                        .planId(2)
                        .title("액션 버전: " + mainChar + "의 역동적인 순간")
                        .focus("역동성과 에너지")
                        .displayText(mainChar + "의 활동")
                        .recommendationReason("높은 에너지")
                        .strengths(java.util.List.of("역동성", "에너지", "시청률"))
                        .targetUseCase("액션 브랜드")
                        .storyLine(mainChar + "이(가) " + background + "에서 " + situation + "한다.")
                        .coreElements(PlanningGenerateResponse.CoreElements.builder()
                                .purpose(purpose)
                                .duration(duration)
                                .ratio(ratio)
                                .style(style)
                                .mainCharacter(mainChar)
                                .subCharacters(java.util.List.of(background))
                                .backgroundWorld(background)
                                .storyFlow("시작 → 상승 → 절정 → 피니시")
                                .storyLine(mainChar + "의 활동")
                                .build())
                        .build(),
                // 유머형
                PlanningGenerateResponse.Plan.builder()
                        .planId(3)
                        .title("유머 버전: " + mainChar + "의 재미있는 일상")
                        .focus("유머와 반전")
                        .displayText(mainChar + "의 유머")
                        .recommendationReason("높은 재미도")
                        .strengths(java.util.List.of("유머", "재미", "공유성"))
                        .targetUseCase("유머 브랜드")
                        .storyLine(mainChar + "이(가) " + background + "에서 " + situation + "한다.")
                        .coreElements(PlanningGenerateResponse.CoreElements.builder()
                                .purpose(purpose)
                                .duration(duration)
                                .ratio(ratio)
                                .style(style)
                                .mainCharacter(mainChar)
                                .subCharacters(java.util.List.of(background))
                                .backgroundWorld(background)
                                .storyFlow("평범 → 반전 → 웃음 → 마무리")
                                .storyLine(mainChar + "의 유머")
                                .build())
                        .build()
        );
    }

    /**
     * userPrompt 기반 동적 분석 fallback
     */
    private PlanAnalysisResponse buildDynamicFallbackAnalysis(
            Long projectId, Integer planId, String userPrompt,
            com.example.hdb.entity.Project project) {

        String prompt = userPrompt != null ? userPrompt : "";
        String mainChar = extractMainCharacter(prompt);
        String background = extractBackground(prompt);
        String situation = extractSituation(prompt);

        PlanAnalysisResponse.ProjectCore fallbackCore = PlanAnalysisResponse.ProjectCore.builder()
                .purpose(project.getPurpose())
                .duration(project.getDuration())
                .ratio(project.getRatio())
                .style(project.getStyle())
                .mainCharacter(mainChar)
                .subCharacters(java.util.List.of(background, "주변 소품들"))
                .backgroundWorld(background)
                .storyFlow("도입 → " + situation + " → 감동적인 마무리")
                .storyLine(String.format("%s이(가) %s에서 %s 이야기", mainChar, background, situation))
                .build();

        List<PlanAnalysisResponse.SceneInfo> fallbackScenes = java.util.List.of(
                PlanAnalysisResponse.SceneInfo.builder()
                        .sceneOrder(1)
                        .summary(String.format("%s이(가) %s에 등장하는 도입 장면", mainChar, background))
                        .sceneGoal("도입과 캐릭터 소개")
                        .emotionBeat("기대감과 호기심")
                        .estimatedDuration(calcDuration(project.getDuration(), 3, 5))
                        .build(),
                PlanAnalysisResponse.SceneInfo.builder()
                        .sceneOrder(2)
                        .summary(String.format("%s이(가) %s 하는 메인 장면", mainChar, situation))
                        .sceneGoal("핵심 내용 전달")
                        .emotionBeat("흥미와 즐거움")
                        .estimatedDuration(calcDuration(project.getDuration(), 7, 10))
                        .build(),
                PlanAnalysisResponse.SceneInfo.builder()
                        .sceneOrder(3)
                        .summary(String.format("%s이(가) 만족스러운 결말을 맺는 마무리 장면", mainChar))
                        .sceneGoal("결말과 여운 전달")
                        .emotionBeat("만족감과 따뜻함")
                        .estimatedDuration(calcDuration(project.getDuration(), 3, 5))
                        .build()
        );

        PlanAnalysisResponse.ScenePlan fallbackScenePlan = PlanAnalysisResponse.ScenePlan.builder()
                .recommendedSceneCount(3)
                .scenes(fallbackScenes)
                .build();

        return PlanAnalysisResponse.builder()
                .projectId(projectId)
                .selectedPlanId(planId)
                .projectCore(fallbackCore)
                .scenePlan(fallbackScenePlan)
                .build();
    }

    // ──────────────────────────────────────────
    // PRIVATE: userPrompt 파싱 헬퍼
    // ──────────────────────────────────────────

    private String extractMainCharacter(String prompt) {
        String lower = prompt.toLowerCase();
        if (lower.contains("강아지") || lower.contains("dog")) return "강아지";
        if (lower.contains("고양이") || lower.contains("cat")) return "고양이";
        if (lower.contains("햄스터") || lower.contains("hamster")) return "햄스터";
        if (lower.contains("토끼") || lower.contains("rabbit")) return "토끼";
        if (lower.contains("곰") || lower.contains("bear")) return "곰";
        if (lower.contains("아기") || lower.contains("baby")) return "아기";
        if (lower.contains("아이") || lower.contains("child")) return "아이";
        if (lower.contains("사람") || lower.contains("person")) return "주인공";
        String[] words = prompt.split("\\s+");
        if (words.length > 0 && !words[0].isBlank()) return words[0];
        return "주인공";
    }

    private String extractSituation(String prompt) {
        String lower = prompt.toLowerCase();
        if (lower.contains("티비") || lower.contains("tv") || lower.contains("텔레비전")) return "티비를 보는";
        if (lower.contains("옷") && lower.contains("갈아입")) return "옷을 갈아입는";
        if (lower.contains("먹") || lower.contains("음식")) return "음식을 먹는";
        if (lower.contains("자고") || lower.contains("잠")) return "귀엽게 자는";
        if (lower.contains("달리") || lower.contains("뛰")) return "신나게 달리는";
        if (lower.contains("춤")) return "신나게 춤추는";
        if (lower.contains("놀")) return "신나게 노는";
        if (lower.contains("산책")) return "산책하는";
        return "귀엽게 활동하는";
    }

    private String extractBackground(String prompt) {
        String lower = prompt.toLowerCase();
        if (lower.contains("소파")) return "아늑한 거실 소파";
        if (lower.contains("거실")) return "따뜻한 거실";
        if (lower.contains("공원") || lower.contains("야외")) return "화창한 공원";
        if (lower.contains("카페")) return "따뜻한 카페";
        if (lower.contains("해변") || lower.contains("바다")) return "아름다운 해변";
        if (lower.contains("옷장") || lower.contains("드레스룸")) return "아기자기한 옷방";
        if (lower.contains("집") || lower.contains("방")) return "아늑한 집";
        return "아늑하고 따뜻한 공간";
    }

    private int calcDuration(Integer projectDuration, int min, int max) {
        if (projectDuration == null) return min;
        int portion = projectDuration / 3;
        return Math.max(min, Math.min(max, portion));
    }

    // ──────────────────────────────────────────
    // PRIVATE: 기타 헬퍼
    // ──────────────────────────────────────────

    private PlanningSummaryResponse createDefaultPlanningSummary(
            Long projectId, com.example.hdb.entity.Project project) {
        return PlanningSummaryResponse.builder()
                .projectId(projectId)
                .selectedPlanId(null)
                .selectedPlanTitle(null)
                .purpose(project.getPurpose())
                .duration(project.getDuration())
                .ratio(project.getRatio())
                .style(project.getStyle())
                .mainCharacter(null)
                .subCharacters(null)
                .backgroundWorld(null)
                .storyFlow(null)
                .storyLine(null)
                .build();
    }

    private void validateProjectOwnership(com.example.hdb.entity.Project project, String loginId) {
        if (!project.getUser().getLoginId().equals(loginId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
    }
}