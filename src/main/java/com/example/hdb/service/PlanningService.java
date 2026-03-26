package com.example.hdb.service;

import com.example.hdb.dto.request.PlanCreateRequest;
import com.example.hdb.dto.response.PlanAnalysisResponse;
import com.example.hdb.dto.response.PlanningGenerateResponse;
import com.example.hdb.dto.response.PlanningSummaryResponse;
import com.example.hdb.dto.response.PromptGenerateResponse;
import com.example.hdb.entity.ProjectPlan;
import com.example.hdb.entity.ProjectStatus;
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
    // 기획 생성
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

            // ★★★ 추가 검증: 3개가 아니면 Fallback ★★★
            if (plans.size() != 3) {
                log.warn("Expected 3 plans but got {}, using fallback", plans.size());
                throw new RuntimeException("Expected 3 plans but got " + plans.size());
            }

            log.info("=== PARSING RESULT (PLANNING) ===");
            for (PlanningGenerateResponse.Plan plan : plans) {
                log.info("planId={}, title={}, mainCharacter={}",
                        plan.getPlanId(),
                        plan.getTitle(),
                        plan.getCoreElements() != null ? plan.getCoreElements().getMainCharacter() : null);

                // storyLine 상세 로그
                if (plan.getStoryLine() != null) {
                    log.info("=== LLM RAW RESPONSE ===");
                    log.info("planId={}, storyLine length: {} chars", plan.getPlanId(), plan.getStoryLine().length());
                    log.info("planId={}, storyLine preview: {}...", plan.getPlanId(),
                            plan.getStoryLine().length() > 100 ? plan.getStoryLine().substring(0, 100) : plan.getStoryLine());

                    // 문장 수 계산
                    String[] sentences = plan.getStoryLine().split("(?<=[.!?])\\s+");
                    log.info("planId={}, sentence count: {}", plan.getPlanId(), sentences.length);

                    log.info("=== FINAL STORED STORYLINE ===");
                    log.info("planId={}, full storyLine: {}", plan.getPlanId(), plan.getStoryLine());
                }
            }
            log.info("=== FALLBACK STATUS: false (LLM response used) ===");

            return PlanningGenerateResponse.builder()
                    .projectId(projectId)
                    .selectedPlanId(project.getSelectedPlanId())
                    .plans(plans)
                    .build();

        } catch (Exception e) {
            log.error("기획 생성 실패 - LLM 응답 파싱 오류, Fallback 사용: {}", e.getMessage());
            log.info("=== FALLBACK STATUS: true (LLM response parsing failed) ===");

            // ★★★ Fallback: 3개 기획안 자동 생성 ★★★
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

        Integer selectedPlanId = project.getSelectedPlanId() != null ? project.getSelectedPlanId() : 1;

        try {
            String planData = latestPlan.get().getPlanData();
            String json = JsonUtils.extractJsonSafely(planData);
            JsonNode root = objectMapper.readTree(json);
            JsonNode plansArray = root.path("plans");

            if (!plansArray.isArray() || plansArray.isEmpty()) {
                return createDefaultPlanningSummary(projectId, project);
            }

            JsonNode selectedPlanJson = plansArray.get(selectedPlanId - 1);
            if (selectedPlanJson == null || selectedPlanJson.isMissingNode()) {
                return createDefaultPlanningSummary(projectId, project);
            }

            JsonNode coreElements = selectedPlanJson.path("coreElements");

            return PlanningSummaryResponse.builder()
                    .projectId(projectId)
                    .selectedPlanId(selectedPlanId)
                    .selectedPlanTitle(selectedPlanJson.path("title").asText(null))
                    .purpose(project.getPurpose())
                    .duration(project.getDuration())
                    .ratio(project.getRatio())
                    .style(project.getStyle())
                    .mainCharacter(coreElements.path("mainCharacter").asText(null))
                    .subCharacters(coreElements.path("subCharacters").isArray()
                            ? objectMapper.convertValue(coreElements.path("subCharacters"), List.class)
                            : null)
                    .backgroundWorld(coreElements.path("backgroundWorld").asText(null))
                    .storyFlow(coreElements.path("storyFlow").asText(null))
                    .storyLine(coreElements.path("storyLine").asText(null))
                    .build();

        } catch (Exception e) {
            log.error("Failed to generate planning summary", e);
            return createDefaultPlanningSummary(projectId, project);
        }
    }

    // ──────────────────────────────────────────
    // 기획안 분석
    // ──────────────────────────────────────────

    public PlanAnalysisResponse analyzeSelectedPlan(Long projectId, Integer planId, String loginId) {
        log.info("=== Plan Analysis Started === projectId={}, planId={}, loginId={}", projectId, planId, loginId);

        var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        Optional<ProjectPlan> latestPlan = getLatestPlan(projectId);
        if (latestPlan.isEmpty()) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }

        String response = null;

        try {
            String planData = latestPlan.get().getPlanData();
            log.info("Latest plan_data JSON: {}", planData);

            JsonNode root = objectMapper.readTree(JsonUtils.extractJsonSafely(planData));
            JsonNode plansArray = root.path("plans");
            if (!plansArray.isArray() || plansArray.isEmpty()) {
                plansArray = root.path("meta").path("storyOptions");
            }

            if (!plansArray.isArray() || plansArray.isEmpty()) {
                throw new BusinessException(ErrorCode.LLM_GENERATION_FAILED);
            }

            if (planId < 1 || planId > plansArray.size()) {
                throw new BusinessException(ErrorCode.LLM_GENERATION_FAILED);
            }

            JsonNode selectedPlanJson = plansArray.get(planId - 1);
            JsonNode coreElements = selectedPlanJson.path("coreElements");

            String storyLine = selectedPlanJson.path("storyLine").asText(
                    coreElements.path("storyLine").asText("")
            );

            if (storyLine.isBlank()) {
                throw new BusinessException(ErrorCode.LLM_GENERATION_FAILED);
            }

            response = openAIService.analyzeSelectedPlan(
                    storyLine,
                    project.getPurpose(),
                    project.getDuration(),
                    project.getRatio(),
                    project.getStyle()
            );

            log.info("=== LLM RAW RESPONSE (ANALYZE) ===");
            log.info("Raw AI Response: {}", response);
            log.info("Input storyLine: {}", storyLine);

            String json = JsonUtils.extractJsonSafely(response);
            JsonNode analysisRoot = objectMapper.readTree(json);

            JsonNode projectCoreNode = analysisRoot.path("projectCore");
            JsonNode scenePlanNode = analysisRoot.path("scenePlan");

            if (projectCoreNode.isMissingNode() || scenePlanNode.isMissingNode()) {
                throw new BusinessException(ErrorCode.LLM_GENERATION_FAILED);
            }

            PlanAnalysisResponse.ProjectCore projectCore = objectMapper.treeToValue(
                    projectCoreNode, PlanAnalysisResponse.ProjectCore.class);

            PlanAnalysisResponse.ScenePlan scenePlan = objectMapper.treeToValue(
                    scenePlanNode, PlanAnalysisResponse.ScenePlan.class);

            PlanAnalysisResponse result = PlanAnalysisResponse.builder()
                    .projectId(projectId)
                    .selectedPlanId(planId)
                    .projectCore(projectCore)
                    .scenePlan(scenePlan)
                    .build();

            // LLM 응답과 최종 저장된 storyLine 비교 로그
            log.info("=== STORYLINE COMPARISON ===");
            log.info("Input storyLine (from plan): {}", storyLine);
            log.info("Saved projectCore.storyLine: {}", projectCore.getStoryLine());
            log.info("StoryLine length: {} chars", storyLine.length());

            savePlanAnalysis(projectId, planId, result);
            return result;

        } catch (Exception e) {
            log.error("기획 분석 실패 - LLM 응답 파싱 오류: {}", e.getMessage(), e);
            log.info("=== FALLBACK STATUS: true (LLM response parsing failed) ===");
            if (response != null) {
                log.error("LLM Raw Response: {}", response);
            }
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "기획 분석에 실패했습니다: " + e.getMessage());
        }
    }
    // ──────────────────────────────────────────

    public PromptGenerateResponse generatePrompt(Long projectId, Long sceneId, String loginId) {
        log.info("=== Prompt Generation Started === projectId={}, sceneId={}, loginId={}", projectId, sceneId, loginId);

        try {
            var scene = sceneRepository.findById(sceneId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));

            var project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

            var planAnalysis = getLatestPlanAnalysis(projectId);
            if (planAnalysis == null) {
                throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
            }

            String projectCoreStr = String.format(
                    "purpose: %s, duration: %d, ratio: %s, style: %s, mainCharacter: %s, backgroundWorld: %s, storyLine: %s",
                    planAnalysis.getProjectCore().getPurpose(),
                    planAnalysis.getProjectCore().getDuration(),
                    planAnalysis.getProjectCore().getRatio(),
                    planAnalysis.getProjectCore().getStyle(),
                    planAnalysis.getProjectCore().getMainCharacter(),
                    planAnalysis.getProjectCore().getBackgroundWorld(),
                    planAnalysis.getProjectCore().getStoryLine()
            );

            String optionalElementsStr = scene.getOptionalElements() != null
                    ? scene.getOptionalElements() : "{}";

            String aiResponse = openAIService.generateFinalPrompts(
                    scene.getSummary(), projectCoreStr, optionalElementsStr);

            log.info("=== LLM RAW RESPONSE (PROMPT) ===");
            log.info("Raw AI Response: {}", aiResponse);

            String json = JsonUtils.extractJsonSafely(aiResponse);
            JsonNode root = objectMapper.readTree(json);

            String imagePrompt = root.path("imagePrompt").asText("");
            String videoPrompt = root.path("videoPrompt").asText("");

            if (imagePrompt.isBlank() || videoPrompt.isBlank()) {
                throw new BusinessException(ErrorCode.LLM_GENERATION_FAILED);
            }

            return PromptGenerateResponse.builder()
                    .sceneId(sceneId)
                    .imagePrompt(imagePrompt)
                    .videoPrompt(videoPrompt)
                    .build();

        } catch (Exception e) {
            log.error("프롬프트 생성 실패 - fallback 실행", e);

            // planAnalysis에서 mainCharacter / background 꺼내서 fallback 반영
            String mainCharacter = "a character";
            String background = "a cozy room";
            try {
                var analysis = getLatestPlanAnalysis(projectId);
                if (analysis != null && analysis.getProjectCore() != null) {
                    if (analysis.getProjectCore().getMainCharacter() != null)
                        mainCharacter = analysis.getProjectCore().getMainCharacter();
                    if (analysis.getProjectCore().getBackgroundWorld() != null)
                        background = analysis.getProjectCore().getBackgroundWorld();
                }
            } catch (Exception ignored) {}

            return PromptGenerateResponse.builder()
                    .sceneId(sceneId)
                    .imagePrompt(String.format(
                            "%s in %s, soft warm lighting, cinematic composition, high quality",
                            mainCharacter, background))
                    .videoPrompt(String.format(
                            "Cinematic video of %s in %s, gentle movement, warm lighting, smooth camera motion",
                            mainCharacter, background))
                    .build();
        }
    }

    // ──────────────────────────────────────────
    // 최신 기획 조회 (DB 캐시)
    // ──────────────────────────────────────────

    public PlanAnalysisResponse getLatestPlanAnalysis(Long projectId) {
        log.info("=== GET LATEST PLAN ANALYSIS === projectId={}", projectId);

        try {
            Optional<ProjectPlan> latestPlan = getLatestPlan(projectId);
            if (latestPlan.isEmpty()) return null;

            ProjectPlan plan = latestPlan.get();

            if (plan.getAnalysisData() != null && !plan.getAnalysisData().trim().isEmpty()) {
                String json = JsonUtils.extractJsonSafely(plan.getAnalysisData());
                JsonNode root = objectMapper.readTree(json);

                JsonNode projectCoreNode = root.path("projectCore");
                JsonNode scenePlanNode = root.path("scenePlan");

                if (!projectCoreNode.isMissingNode() && !scenePlanNode.isMissingNode()) {
                    PlanAnalysisResponse.ProjectCore projectCore = objectMapper.treeToValue(
                            projectCoreNode, PlanAnalysisResponse.ProjectCore.class);
                    PlanAnalysisResponse.ScenePlan scenePlan = objectMapper.treeToValue(
                            scenePlanNode, PlanAnalysisResponse.ScenePlan.class);

                    return PlanAnalysisResponse.builder()
                            .projectId(projectId)
                            .selectedPlanId(plan.getProject().getSelectedPlanId())
                            .projectCore(projectCore)
                            .scenePlan(scenePlan)
                            .build();
                }
            }

            return null;

        } catch (Exception e) {
            log.error("Failed to load plan analysis from cache", e);
            return null;
        }
    }

    public PlanningGenerateResponse getLatestPlanning(Long projectId, String loginId) {
        log.info("=== GET LATEST PLANNING === projectId={}, loginId={}", projectId, loginId);

        var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        if (!project.getUser().getLoginId().equals(loginId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }

        Optional<ProjectPlan> latestPlan = getLatestPlan(projectId);
        if (latestPlan.isEmpty()) {
            throw new BusinessException(ErrorCode.PLAN_NOT_FOUND);
        }

        ProjectPlan plan = latestPlan.get();
        String planDataJson = plan.getPlanData();

        if (planDataJson == null || planDataJson.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PLAN_DATA_CORRUPTED);
        }

        try {
            JsonNode root = objectMapper.readTree(planDataJson);
            JsonNode plansNode = root.path("plans");

            if (!plansNode.isArray() || plansNode.isEmpty()) {
                throw new BusinessException(ErrorCode.PLAN_DATA_CORRUPTED);
            }

            List<PlanningGenerateResponse.Plan> plans = objectMapper.convertValue(
                    plansNode, new TypeReference<List<PlanningGenerateResponse.Plan>>() {});

            return PlanningGenerateResponse.builder()
                    .projectId(projectId)
                    .selectedPlanId(project.getSelectedPlanId())
                    .plans(plans)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse planData for planId={}", plan.getId(), e);
            throw new BusinessException(ErrorCode.PLAN_DATA_CORRUPTED);
        }
    }

    // ──────────────────────────────────────────
    // 기획 저장
    // ──────────────────────────────────────────

    public ProjectPlan createPlan(String loginId, Long projectId, PlanCreateRequest request) {
        var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        if (!project.getUser().getLoginId().equals(loginId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }

        int nextVersion = projectPlanRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream().mapToInt(ProjectPlan::getVersion).max().orElse(0) + 1;

        try {
            String aiResponse = openAIService.generatePlanningWithSummary(
                    request.getUserPrompt(),
                    project.getPurpose(),
                    project.getDuration(),
                    project.getRatio(),
                    project.getStyle()
            );

            String json = JsonUtils.extractJsonSafely(aiResponse);

            ProjectPlan plan = ProjectPlan.builder()
                    .project(project)
                    .version(nextVersion)
                    .planData(json)
                    .userPrompt(request.getUserPrompt())
                    .status(ProjectPlan.PlanStatus.DRAFT)
                    .build();

            ProjectPlan savedPlan = projectPlanRepository.save(plan);
            project.setStatus(ProjectStatus.PLANNING);
            projectRepository.save(project);

            return savedPlan;

        } catch (Exception e) {
            log.error("기획 생성 실패 - userPrompt 기반 fallback 실행", e);

            List<PlanningGenerateResponse.Plan> fallbackPlans = buildDynamicFallbackPlans(
                    request.getUserPrompt(),
                    project.getPurpose(),
                    project.getDuration(),
                    project.getRatio(),
                    project.getStyle()
            );

            String fallbackJson;
            try {
                fallbackJson = objectMapper.writeValueAsString(
                        java.util.Map.of("plans", fallbackPlans));
            } catch (Exception jsonEx) {
                fallbackJson = "{\"plans\":[]}";
            }

            ProjectPlan plan = ProjectPlan.builder()
                    .project(project)
                    .version(nextVersion)
                    .planData(fallbackJson)
                    .userPrompt(request.getUserPrompt())
                    .status(ProjectPlan.PlanStatus.DRAFT)
                    .build();

            ProjectPlan savedPlan = projectPlanRepository.save(plan);
            project.setStatus(ProjectStatus.PLANNING);
            projectRepository.save(project);

            return savedPlan;
        }
    }

    // ──────────────────────────────────────────
    // 공통 조회
    // ──────────────────────────────────────────

    public Optional<ProjectPlan> getLatestPlan(Long projectId) {
        return projectPlanRepository.findFirstByProjectIdOrderByCreatedAtDesc(projectId);
    }

    public List<ProjectPlan> getPlanHistory(Long projectId) {
        return projectPlanRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    // ──────────────────────────────────────────
    // PRIVATE: userPrompt 기반 동적 Fallback 생성
    // ──────────────────────────────────────────

    /**
     * userPrompt를 분석해서 핵심 소재(캐릭터, 상황, 배경)를 추출하고
     * 3개의 fallback 기획안을 동적으로 생성합니다.
     */
    private List<PlanningGenerateResponse.Plan> buildDynamicFallbackPlans(
            String userPrompt, String purpose, Integer duration, String ratio, String style) {

        String prompt = userPrompt != null ? userPrompt : "";
        String mainChar = extractMainCharacter(prompt);
        String situation = extractSituation(prompt);
        String background = extractBackground(prompt);
        String mood = extractMood(prompt);

        log.info("=== DYNAMIC FALLBACK PLANS ===");
        log.info("mainChar={}, situation={}, background={}, mood={}", mainChar, situation, background, mood);

        return java.util.List.of(
                PlanningGenerateResponse.Plan.builder()
                        .planId(1)
                        .title(mainChar + " - 감성 영상")
                        .focus("감성 중심")
                        .displayText(String.format("%s이(가) %s 따뜻하고 감성적인 숏폼 영상", mainChar, situation))
                        .recommendationReason("사용자 요청의 감성적인 면을 최대한 반영한 기획안")
                        .strengths(java.util.List.of("따뜻한 감성", "캐릭터 중심", "공감 유도"))
                        .targetUseCase("감성 브랜딩 영상")
                        .storyLine(String.format(
                                "%s은(는) %s에서 %s을(를) 마주한 순간, 처음에는 망설이고 불안해하지만 곧 자신감을 찾아 나선다. " +
                                        "주변의 %s은(는) 계속해서 도전을 앞에 두고, %s은(는) 여러 번의 실패와 좌절을 겪으며 지쳐가는 듯하다. " +
                                        "하지만 우연히 만난 %s은(는) 새로운 관점과 해결책을 제시해주며, %s은(는) 다시 일어서 마지막 도전을 시도한다. " +
                                        "마침내 %s을(를) 성공시키는 %s은(는) 주변의 모든 것을 변화시키고, 자신의 진정한 가능성을 발견하게 된다.",
                                mainChar, background, situation, background, mainChar, "브랜드 제품", mainChar, situation, mainChar))
                        .coreElements(PlanningGenerateResponse.CoreElements.builder()
                                .purpose(purpose)
                                .duration(duration)
                                .ratio(ratio)
                                .style(style)
                                .mainCharacter(mainChar)
                                .subCharacters(java.util.List.of(background + " 배경", "주변 소품들"))
                                .backgroundWorld(background)
                                .storyFlow("도입 → " + situation + " → 감동적인 마무리")
                                .storyLine(String.format("%s이(가) %s에서 따뜻한 순간을 보내는 이야기", mainChar, background))
                                .build())
                        .build(),
                PlanningGenerateResponse.Plan.builder()
                        .planId(2)
                        .title(mainChar + " - 역동적인 영상")
                        .focus("액션/역동성 중심")
                        .displayText(String.format("%s이(가) %s 에너지 넘치는 숏폼 영상", mainChar, situation))
                        .recommendationReason("빠른 템포와 강한 시각 임팩트로 짧은 시간 안에 강렬한 인상을 남기는 기획안")
                        .strengths(java.util.List.of("빠른 템포", "강한 시각 임팩트", "숏폼 최적화"))
                        .targetUseCase("바이럴 숏폼 영상")
                        .storyLine(String.format(
                                "%s은(는) %s의 중심에서 갑자기 %s을(를) 마주하게 된다. " +
                                        "순간적으로 당황하지만, %s은(는) 즉시 자신의 능력을 발휘하며 역동적으로 대응하기 시작한다. " +
                                        "%s은(는) 빠른 움직임으로 장애물을 극복하고, 주변의 모든 것을 자신의 에너지로 움직이게 만든다. " +
                                        "도전이 거세질수록 %s은(는) 더욱 강력해지며, 마침내 %s을(를) 완벽하게 해결한다. " +
                                        "성공의 순간, %s은(는) 주변을 환화시키며 새로운 에너지를 뿜어낸다.",
                                mainChar, background, situation, mainChar, mainChar, mainChar, situation, mainChar))
                        .coreElements(PlanningGenerateResponse.CoreElements.builder()
                                .purpose(purpose)
                                .duration(duration)
                                .ratio(ratio)
                                .style(style)
                                .mainCharacter(mainChar)
                                .subCharacters(java.util.List.of("역동적인 소품", "다양한 각도의 카메라"))
                                .backgroundWorld(background)
                                .storyFlow("강렬한 등장 → " + situation + " → 임팩트 있는 마무리")
                                .storyLine(String.format("%s이(가) %s에서 에너지 넘치게 활동하는 이야기", mainChar, background))
                                .build())
                        .build(),
                PlanningGenerateResponse.Plan.builder()
                        .planId(3)
                        .title(mainChar + " - 유머 반전 영상")
                        .focus("유머/반전 중심")
                        .displayText(String.format("%s이(가) %s 예상치 못한 반전이 있는 유쾌한 숏폼 영상", mainChar, situation))
                        .recommendationReason("웃음 포인트와 귀여운 반전으로 공유성이 높은 기획안")
                        .strengths(java.util.List.of("유머 포인트", "귀여운 반전", "높은 공유성"))
                        .targetUseCase("바이럴 중심 캐릭터 영상")
                        .storyLine(String.format(
                                "%s은(는) %s에서 평범한 일상을 보내고 있다가, 갑자기 %s을(를) 마주하게 된다. " +
                                        "%s은(는) 엉뚱한 방식으로 상황을 해결하려 하지만, 계속해서 더 웃긴 상황으로 만든다. " +
                                        "주변의 모든 것이 %s의 실수를 증폭시키고, 상황은 점점 더 코믹하게 변해간다. " +
                                        "하지만 우연히 발견한 %s은(는) 예상치 못한 해결책이 되어, 모든 것을 역전시킨다. " +
                                        "%s은(는) 결국 웃음으로 문제를 해결하고, 주변 모두를 즐겁게 만든다.",
                                mainChar, background, situation, mainChar, mainChar, "브랜드 제품", mainChar))
                        .coreElements(PlanningGenerateResponse.CoreElements.builder()
                                .purpose(purpose)
                                .duration(duration)
                                .ratio(ratio)
                                .style(style)
                                .mainCharacter(mainChar)
                                .subCharacters(java.util.List.of("반전 소품", "예상치 못한 요소"))
                                .backgroundWorld(background)
                                .storyFlow("평범한 시작 → 예상 못한 상황 → 웃음 포인트 → 유쾌한 마무리")
                                .storyLine(String.format("%s이(가) %s에서 엉뚱한 실수를 하다가 의외의 결과를 내는 이야기", mainChar, background))
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

    private String extractMood(String prompt) {
        String lower = prompt.toLowerCase();
        if (lower.contains("따뜻") || lower.contains("감성")) return "따뜻하고 포근한";
        if (lower.contains("귀엽") || lower.contains("cute")) return "귀엽고 사랑스러운";
        if (lower.contains("재밌") || lower.contains("유머")) return "유쾌하고 재밌는";
        if (lower.contains("역동") || lower.contains("활기")) return "활기차고 역동적인";
        return "귀엽고 따뜻한";
    }

    private int calcDuration(Integer projectDuration, int min, int max) {
        if (projectDuration == null) return min;
        int portion = projectDuration / 3;
        return Math.max(min, Math.min(max, portion));
    }

    // ──────────────────────────────────────────
    // PRIVATE: 기타 헬퍼
    // ──────────────────────────────────────────

    private void savePlanAnalysis(Long projectId, Integer selectedPlanId, PlanAnalysisResponse analysis) {
        log.info("=== SAVE PLAN ANALYSIS === projectId={}", projectId);
        try {
            Optional<ProjectPlan> latestPlan = getLatestPlan(projectId);
            if (latestPlan.isEmpty()) return;

            ProjectPlan plan = latestPlan.get();
            String analysisJson = objectMapper.writeValueAsString(analysis);
            plan.setAnalysisData(analysisJson);
            projectPlanRepository.save(plan);
        } catch (Exception e) {
            log.error("Failed to save plan analysis to DB", e);
        }
    }

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
}