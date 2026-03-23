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

    public PlanningGenerateResponse generatePlanning(Long projectId, String userPrompt, String loginId) {
        log.info("=== Planning Generation Started ===");
        log.info("Project ID: {}", projectId);
        log.info("User Prompt: {}", userPrompt);
        log.info("Login ID: {}", loginId);

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
                throw new BusinessException(ErrorCode.LLM_GENERATION_FAILED);
            }

            List<PlanningGenerateResponse.Plan> plans = objectMapper.convertValue(
                    plansNode,
                    new TypeReference<List<PlanningGenerateResponse.Plan>>() {}
            );

            log.info("=== PARSING RESULT (PLANNING) ===");
            for (PlanningGenerateResponse.Plan plan : plans) {
                log.info("planId={}, title={}, mainCharacter={}",
                        plan.getPlanId(),
                        plan.getTitle(),
                        plan.getCoreElements() != null ? plan.getCoreElements().getMainCharacter() : null);
            }
            log.info("=== FALLBACK STATUS: false (LLM response used) ===");

            return PlanningGenerateResponse.builder()
                    .projectId(projectId)
                    .selectedPlanId(project.getSelectedPlanId())
                    .plans(plans)
                    .build();

        } catch (Exception e) {
            log.error("기획 생성 실패 - fallback 실행", e);
            log.info("=== FALLBACK STATUS: true (LLM response failed) ===");

            List<PlanningGenerateResponse.Plan> fallbackPlans = java.util.List.of(
                    PlanningGenerateResponse.Plan.builder()
                            .planId(1)
                            .title("햄스터 옷갈아입기 트랜스포메이션")
                            .focus("감성 중심")
                            .displayText("작은 햄스터가 마법 같은 옷장 속에서 여러 의상을 갈아입으며 귀엽게 변신하는 따뜻한 숏폼 광고")
                            .recommendationReason("사용자 요청을 가장 직접적으로 반영한 귀여운 변신 콘셉트")
                            .strengths(java.util.List.of("햄스터 중심 캐릭터성", "변신의 재미", "따뜻한 감성"))
                            .targetMood("포근하고 귀여운 분위기")
                            .targetUseCase("캐릭터 브랜딩 광고")
                            .storyLine("작은 햄스터가 옷장을 열고 하나씩 다른 옷을 입어보며 자신에게 가장 잘 어울리는 스타일을 찾고, 마지막에 행복하게 미소 짓는 이야기")
                            .coreElements(PlanningGenerateResponse.CoreElements.builder()
                                    .purpose(project.getPurpose())
                                    .duration(project.getDuration())
                                    .ratio(project.getRatio())
                                    .style(project.getStyle())
                                    .mainCharacter("작고 귀여운 햄스터")
                                    .subCharacters(java.util.List.of("마법 옷장", "거울 속 햄스터"))
                                    .backgroundWorld("작은 옷방이 있는 아기자기한 햄스터 집")
                                    .storyFlow("옷장 발견 → 여러 옷 시도 → 가장 잘 어울리는 옷 선택 → 만족스러운 마무리")
                                    .storyLine("햄스터가 옷을 갈아입으며 자신만의 스타일을 찾는 따뜻한 이야기")
                                    .build())
                            .build(),
                    PlanningGenerateResponse.Plan.builder()
                            .planId(2)
                            .title("햄스터 패션쇼 런웨이")
                            .focus("액션 중심")
                            .displayText("작은 햄스터가 미니 런웨이 위에서 빠르게 여러 의상을 선보이며 에너지 넘치게 변신하는 숏폼 광고")
                            .recommendationReason("짧은 시간 안에 역동적인 장면과 패션 포인트를 강하게 보여줄 수 있음")
                            .strengths(java.util.List.of("빠른 템포", "강한 시각 임팩트", "숏폼 최적화"))
                            .targetMood("활기차고 에너지 있는 분위기")
                            .targetUseCase("트렌디한 패션 광고")
                            .storyLine("햄스터가 작은 런웨이에 올라 다양한 의상을 빠르게 갈아입으며 등장하고, 마지막에 가장 멋진 포즈로 패션쇼를 완성하는 이야기")
                            .coreElements(PlanningGenerateResponse.CoreElements.builder()
                                    .purpose(project.getPurpose())
                                    .duration(project.getDuration())
                                    .ratio(project.getRatio())
                                    .style(project.getStyle())
                                    .mainCharacter("패션쇼에 도전하는 햄스터")
                                    .subCharacters(java.util.List.of("관객 역할의 장난감 인형", "스포트라이트"))
                                    .backgroundWorld("미니 런웨이와 조명이 있는 작은 패션 무대")
                                    .storyFlow("런웨이 등장 → 옷 갈아입기 변신 → 런웨이 워킹 → 피날레 포즈")
                                    .storyLine("햄스터가 런웨이에서 여러 의상을 선보이며 자신감 있게 패션쇼를 완성하는 이야기")
                                    .build())
                            .build(),
                    PlanningGenerateResponse.Plan.builder()
                            .planId(3)
                            .title("햄스터의 엉뚱한 옷장 해프닝")
                            .focus("유머/반전 중심")
                            .displayText("햄스터가 엉뚱한 옷들을 잘못 고르며 귀여운 실수를 반복하다가 의외로 완벽한 스타일을 찾는 유쾌한 숏폼 광고")
                            .recommendationReason("웃음 포인트와 귀여운 반전을 동시에 만들 수 있음")
                            .strengths(java.util.List.of("유머 포인트", "귀여운 반전", "높은 공유성"))
                            .targetMood("유쾌하고 장난스러운 분위기")
                            .targetUseCase("바이럴 중심 캐릭터 광고")
                            .storyLine("햄스터가 너무 큰 모자나 엉뚱한 옷을 입어보며 실수하지만, 마지막엔 의외로 완벽하게 어울리는 옷을 찾아 모두를 놀라게 하는 이야기")
                            .coreElements(PlanningGenerateResponse.CoreElements.builder()
                                    .purpose(project.getPurpose())
                                    .duration(project.getDuration())
                                    .ratio(project.getRatio())
                                    .style(project.getStyle())
                                    .mainCharacter("호기심 많은 장난꾸러기 햄스터")
                                    .subCharacters(java.util.List.of("넘어지는 모자", "엉뚱한 소품들"))
                                    .backgroundWorld("장난감과 옷이 가득한 햄스터 옷장 공간")
                                    .storyFlow("엉뚱한 옷 선택 → 귀여운 실수 → 예상 못한 반전 → 완벽한 스타일 완성")
                                    .storyLine("햄스터가 실수 끝에 의외의 완벽한 스타일을 찾는 유쾌한 이야기")
                                    .build())
                            .build()
            );

            return PlanningGenerateResponse.builder()
                    .projectId(projectId)
                    .selectedPlanId(null)
                    .plans(fallbackPlans)
                    .build();
        }
    }

    public void selectPlan(Long projectId, Integer selectedPlanId) {
        log.info("=== Plan Selection Started ===");
        log.info("Project ID: {}", projectId);
        log.info("Selected Plan ID: {}", selectedPlanId);

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

        log.info("Plan selection completed - Project: {}, SelectedPlan: {}", projectId, selectedPlanId);
    }

    public PlanningSummaryResponse getPlanningSummary(Long projectId) {
        log.info("=== Planning Summary Started ===");
        log.info("Project ID: {}", projectId);

        var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        Optional<ProjectPlan> latestPlan = getLatestPlan(projectId);
        if (latestPlan.isEmpty()) {
            return createDefaultPlanningSummary(projectId, project);
        }

        Integer selectedPlanId = project.getSelectedPlanId();
        if (selectedPlanId == null) {
            selectedPlanId = 1;
        }

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

    public PlanAnalysisResponse analyzeSelectedPlan(Long projectId, Integer planId, String loginId) {
        log.info("=== Plan Analysis Started ===");
        log.info("Project ID: {}, Plan ID: {}, User: {}", projectId, planId, loginId);

        var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        Optional<ProjectPlan> latestPlan = getLatestPlan(projectId);
        if (latestPlan.isEmpty()) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }

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

            String aiResponse = openAIService.analyzeSelectedPlan(
                    storyLine,
                    project.getPurpose(),
                    project.getDuration(),
                    project.getRatio(),
                    project.getStyle()
            );

            log.info("=== LLM RAW RESPONSE (ANALYZE) ===");
            log.info("Raw AI Response: {}", aiResponse);
            log.info("Input storyLine: {}", storyLine);

            String json = JsonUtils.extractJsonSafely(aiResponse);
            JsonNode analysisRoot = objectMapper.readTree(json);

            JsonNode projectCoreNode = analysisRoot.path("projectCore");
            JsonNode scenePlanNode = analysisRoot.path("scenePlan");

            if (projectCoreNode.isMissingNode() || scenePlanNode.isMissingNode()) {
                throw new BusinessException(ErrorCode.LLM_GENERATION_FAILED);
            }

            PlanAnalysisResponse.ProjectCore projectCore = objectMapper.treeToValue(
                    projectCoreNode,
                    PlanAnalysisResponse.ProjectCore.class
            );

            PlanAnalysisResponse.ScenePlan scenePlan = objectMapper.treeToValue(
                    scenePlanNode,
                    PlanAnalysisResponse.ScenePlan.class
            );

            PlanAnalysisResponse result = PlanAnalysisResponse.builder()
                    .projectId(projectId)
                    .selectedPlanId(planId)
                    .projectCore(projectCore)
                    .scenePlan(scenePlan)
                    .build();

            savePlanAnalysis(projectId, planId, result);

            log.info("=== FALLBACK STATUS: false (LLM response used) ===");
            return result;

        } catch (Exception e) {
            log.error("기획안 분석 실패 - fallback 실행", e);
            log.info("=== FALLBACK STATUS: true (LLM response failed) ===");

            PlanAnalysisResponse.ProjectCore fallbackCore = PlanAnalysisResponse.ProjectCore.builder()
                    .purpose(project.getPurpose())
                    .duration(project.getDuration())
                    .ratio(project.getRatio())
                    .style(project.getStyle())
                    .mainCharacter("작고 귀여운 햄스터")
                    .subCharacters(java.util.List.of("마법 옷장", "거울 속 햄스터"))
                    .backgroundWorld("아기자기한 작은 옷방이 있는 햄스터 집")
                    .storyFlow("옷장 발견 → 여러 옷을 시도 → 가장 잘 어울리는 스타일 선택 → 자신감 있는 마무리")
                    .storyLine("햄스터가 여러 옷을 갈아입으며 자신에게 가장 잘 어울리는 스타일을 찾는 따뜻한 이야기")
                    .build();

            List<PlanAnalysisResponse.SceneInfo> fallbackScenes = java.util.List.of(
                    PlanAnalysisResponse.SceneInfo.builder()
                            .sceneOrder(1)
                            .summary("햄스터가 작은 옷장 앞에서 첫 옷을 꺼내며 기대하는 장면")
                            .sceneGoal("도입과 호기심 형성")
                            .emotionBeat("기대감")
                            .estimatedDuration(5)
                            .build(),
                    PlanAnalysisResponse.SceneInfo.builder()
                            .sceneOrder(2)
                            .summary("햄스터가 다양한 옷을 번갈아 입으며 귀엽게 변신하는 장면")
                            .sceneGoal("변신의 재미 전달")
                            .emotionBeat("흥미와 즐거움")
                            .estimatedDuration(10)
                            .build(),
                    PlanAnalysisResponse.SceneInfo.builder()
                            .sceneOrder(3)
                            .summary("햄스터가 가장 마음에 드는 옷을 입고 거울 앞에서 만족스럽게 미소 짓는 장면")
                            .sceneGoal("결말과 만족감 전달")
                            .emotionBeat("만족감")
                            .estimatedDuration(5)
                            .build()
            );

            PlanAnalysisResponse.ScenePlan fallbackScenePlan = PlanAnalysisResponse.ScenePlan.builder()
                    .recommendedSceneCount(3)
                    .scenes(fallbackScenes)
                    .build();

            PlanAnalysisResponse result = PlanAnalysisResponse.builder()
                    .projectId(projectId)
                    .selectedPlanId(planId)
                    .projectCore(fallbackCore)
                    .scenePlan(fallbackScenePlan)
                    .build();

            savePlanAnalysis(projectId, planId, result);
            return result;
        }
    }

    public PromptGenerateResponse generatePrompt(Long projectId, Long sceneId, String loginId) {
        log.info("=== Prompt Generation Started ===");
        log.info("Project ID: {}, Scene ID: {}, User: {}", projectId, sceneId, loginId);

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
                    ? scene.getOptionalElements()
                    : "{}";

            String aiResponse = openAIService.generateFinalPrompts(
                    scene.getSummary(),
                    projectCoreStr,
                    optionalElementsStr
            );

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

            return PromptGenerateResponse.builder()
                    .sceneId(sceneId)
                    .imagePrompt("A cute hamster trying on clothes in a warm cozy room, soft lighting, cinematic composition")
                    .videoPrompt("A cute hamster changes clothes in a warm cozy room, gentle movement, soft lighting, cinematic video")
                    .build();
        }
    }

    public PlanAnalysisResponse getLatestPlanAnalysis(Long projectId) {
        log.info("=== GET LATEST PLAN ANALYSIS ===");

        try {
            Optional<ProjectPlan> latestPlan = getLatestPlan(projectId);
            if (latestPlan.isEmpty()) {
                return null;
            }

            ProjectPlan plan = latestPlan.get();

            if (plan.getAnalysisData() != null && !plan.getAnalysisData().trim().isEmpty()) {
                String json = JsonUtils.extractJsonSafely(plan.getAnalysisData());
                JsonNode root = objectMapper.readTree(json);

                JsonNode projectCoreNode = root.path("projectCore");
                JsonNode scenePlanNode = root.path("scenePlan");

                if (!projectCoreNode.isMissingNode() && !scenePlanNode.isMissingNode()) {
                    PlanAnalysisResponse.ProjectCore projectCore = objectMapper.treeToValue(
                            projectCoreNode,
                            PlanAnalysisResponse.ProjectCore.class
                    );
                    PlanAnalysisResponse.ScenePlan scenePlan = objectMapper.treeToValue(
                            scenePlanNode,
                            PlanAnalysisResponse.ScenePlan.class
                    );

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

    private void savePlanAnalysis(Long projectId, Integer selectedPlanId, PlanAnalysisResponse analysis) {
        log.info("=== SAVE PLAN ANALYSIS ===");

        try {
            Optional<ProjectPlan> latestPlan = getLatestPlan(projectId);
            if (latestPlan.isEmpty()) {
                return;
            }

            ProjectPlan plan = latestPlan.get();
            String analysisJson = objectMapper.writeValueAsString(analysis);
            plan.setAnalysisData(analysisJson);
            projectPlanRepository.save(plan);

        } catch (Exception e) {
            log.error("Failed to save plan analysis to DB", e);
        }
    }

    private PlanningSummaryResponse createDefaultPlanningSummary(Long projectId, com.example.hdb.entity.Project project) {
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

    public ProjectPlan createPlan(String loginId, Long projectId, PlanCreateRequest request) {
        var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        if (!project.getUser().getLoginId().equals(loginId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }

        int nextVersion = projectPlanRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream()
                .mapToInt(ProjectPlan::getVersion)
                .max()
                .orElse(0) + 1;

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
            log.error("기획 생성 실패 - fallback 실행", e);

            String fallbackJson = objectMapper.createObjectNode()
                    .putPOJO("plans", generatePlanning(projectId, request.getUserPrompt(), loginId).getPlans())
                    .toString();

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

    public Optional<ProjectPlan> getLatestPlan(Long projectId) {
        return projectPlanRepository.findFirstByProjectIdOrderByCreatedAtDesc(projectId);
    }

    public List<ProjectPlan> getPlanHistory(Long projectId) {
        return projectPlanRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
    }
}