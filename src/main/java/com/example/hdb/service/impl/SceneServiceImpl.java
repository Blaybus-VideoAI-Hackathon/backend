package com.example.hdb.service.impl;

import com.example.hdb.dto.common.OptionalElements;
import com.example.hdb.dto.request.*;
import com.example.hdb.dto.response.ProjectPlanResponse;
import com.example.hdb.dto.response.SceneDesignResponse;
import com.example.hdb.dto.response.SceneGenerationResponse.SceneSummaryDto;
import com.example.hdb.dto.response.SceneResponse;
import com.example.hdb.entity.Project;
import com.example.hdb.entity.Scene;
import com.example.hdb.enums.SceneStatus;
import com.example.hdb.exception.BusinessException;
import com.example.hdb.exception.ErrorCode;
import com.example.hdb.repository.ProjectRepository;
import com.example.hdb.repository.SceneImageRepository;
import com.example.hdb.repository.SceneRepository;
import com.example.hdb.repository.SceneVideoRepository;
import com.example.hdb.service.OpenAIService;
import com.example.hdb.service.PlanningService;
import com.example.hdb.service.SceneService;
import com.example.hdb.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class SceneServiceImpl implements SceneService {

    private final SceneRepository sceneRepository;
    private final ProjectRepository projectRepository;
    private final ObjectMapper objectMapper;
    private final OpenAIService openAIService;
    private final SceneImageRepository sceneImageRepository;
    private final SceneVideoRepository sceneVideoRepository;
    private final PlanningService planningService;

    // ──────────────────────────────────────────
    // CREATE / UPDATE / DELETE
    // ──────────────────────────────────────────

    @Override
    public SceneResponse createScene(SceneCreateRequest request) {
        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        if (sceneRepository.existsByProjectIdAndSceneOrder(request.getProjectId(), request.getSceneOrder())) {
            throw new BusinessException(ErrorCode.SCENE_ORDER_DUPLICATE);
        }

        Scene scene = Scene.builder()
                .project(project)
                .sceneOrder(request.getSceneOrder())
                .summary(request.getSummary())
                .optionalElements(request.getOptionalElements())
                .imagePrompt(request.getImagePrompt())
                .videoPrompt(request.getVideoPrompt())
                .status(request.getStatus())
                .build();

        return SceneResponse.from(sceneRepository.save(scene));
    }

    @Override
    public SceneResponse updateScene(Long sceneId, SceneUpdateRequest request) {
        Scene scene = sceneRepository.findById(sceneId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));

        scene.setSummary(request.getSummary());
        scene.setOptionalElements(request.getOptionalElements());
        scene.setImagePrompt(request.getImagePrompt());
        scene.setVideoPrompt(request.getVideoPrompt());

        return SceneResponse.from(sceneRepository.save(scene));
    }

    @Override
    public SceneResponse updateSceneAndCheckPermission(Long sceneId, Long userId, SceneUpdateRequest request) {
        Scene scene = sceneRepository.findByIdAndProjectUserId(sceneId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));

        scene.setSummary(request.getSummary());
        scene.setOptionalElements(request.getOptionalElements());
        scene.setImagePrompt(request.getImagePrompt());
        scene.setVideoPrompt(request.getVideoPrompt());

        Scene updatedScene = sceneRepository.save(scene);

        String imageUrl = getRepresentativeImageUrl(updatedScene.getId());
        String videoUrl = getRepresentativeVideoUrl(updatedScene.getId());

        return SceneResponse.from(updatedScene, null, imageUrl, videoUrl);
    }

    @Override
    public void deleteScene(Long projectId, Long sceneId, String loginId) {
        Scene scene = sceneRepository.findByIdAndProjectId(sceneId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));

        if (!scene.getProject().getUser().getLoginId().equals(loginId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }

        sceneImageRepository.deleteAllBySceneId(sceneId);
        sceneVideoRepository.deleteAllBySceneId(sceneId);
        sceneRepository.delete(scene);
    }

    // ──────────────────────────────────────────
    // READ
    // ──────────────────────────────────────────

    @Override
    public List<SceneResponse> getScenesByProjectId(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }

        return sceneRepository.findByProjectIdOrderBySceneOrderAsc(projectId).stream()
                .map(scene -> SceneResponse.from(
                        scene,
                        parseOptionalElements(scene.getOptionalElements()),
                        getRepresentativeImageUrl(scene.getId()),
                        getRepresentativeVideoUrl(scene.getId())
                ))
                .collect(Collectors.toList());
    }

    // ──────────────────────────────────────────
    // SCENE GENERATION
    // ──────────────────────────────────────────

    @Override
    public List<SceneSummaryDto> generateScenes(Long projectId, Integer selectedPlanId, String loginId) {
        log.info("=== Scene Generation Started ===");
        log.info("projectId={}, selectedPlanId={}, loginId={}", projectId, selectedPlanId, loginId);

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        // 기존 씬 삭제
        List<Scene> existingScenes = sceneRepository.findByProjectId(projectId);
        if (!existingScenes.isEmpty()) {
            sceneRepository.deleteByProjectId(projectId);
            log.info("Deleted {} existing scenes for project {}", existingScenes.size(), projectId);
        }

        // 1. DB 캐시에서 분석 결과 사용 시도
        try {
            var planAnalysis = planningService.getLatestPlanAnalysis(projectId);
            if (planAnalysis != null
                    && planAnalysis.getScenePlan() != null
                    && planAnalysis.getScenePlan().getScenes() != null
                    && !planAnalysis.getScenePlan().getScenes().isEmpty()) {

                log.info("=== USING CACHED PLAN ANALYSIS ===");
                log.info("sceneCount={}", planAnalysis.getScenePlan().getScenes().size());
                log.info("=== FALLBACK STATUS: false (cached analysis used) ===");

                List<Scene> generatedScenes = planAnalysis.getScenePlan().getScenes().stream()
                        .map(sceneInfo -> Scene.builder()
                                .project(project)
                                .sceneOrder(sceneInfo.getSceneOrder())
                                .summary(sceneInfo.getSummary())
                                .status(SceneStatus.PENDING)
                                .build())
                        .collect(Collectors.toList());

                List<Scene> savedScenes = sceneRepository.saveAll(generatedScenes);
                return toSceneSummaryDtoList(savedScenes);
            }
        } catch (Exception e) {
            log.warn("Cached plan analysis not available, running fresh analysis: {}", e.getMessage());
        }

        // 2. 캐시 없으면 analyzeSelectedPlan 호출 후 씬 생성
        log.info("=== FALLBACK STATUS: true — running fresh analyzeSelectedPlan ===");
        try {
            var analysis = planningService.analyzeSelectedPlan(projectId, selectedPlanId, loginId);

            List<Scene> fallbackScenes = analysis.getScenePlan().getScenes().stream()
                    .map(sceneInfo -> Scene.builder()
                            .project(project)
                            .sceneOrder(sceneInfo.getSceneOrder())
                            .summary(sceneInfo.getSummary())
                            .status(SceneStatus.PENDING)
                            .build())
                    .collect(Collectors.toList());

            List<Scene> savedScenes = sceneRepository.saveAll(fallbackScenes);
            return toSceneSummaryDtoList(savedScenes);

        } catch (Exception e) {
            log.error("씬 생성 완전 실패 - 하드코딩 fallback 사용", e);
            throw new BusinessException(ErrorCode.LLM_GENERATION_FAILED);
        }
    }

    // ──────────────────────────────────────────
    // SCENE DESIGN
    // ──────────────────────────────────────────

    @Override
    public SceneResponse designScene(Long projectId, Long sceneId, String loginId, SceneDesignRequest request) {
        log.info("=== Scene Design Started ===");
        log.info("sceneId={}, projectId={}, loginId={}, designRequest={}",
                sceneId, projectId, loginId, request.getDesignRequest());

        Scene scene = sceneRepository.findByIdAndProjectId(sceneId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));

        if (!scene.getProject().getUser().getLoginId().equals(loginId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }

        try {
            var planAnalysis = planningService.getLatestPlanAnalysis(projectId);
            String projectCoreStr = buildProjectCoreStr(planAnalysis);

            String aiResponse = openAIService.designScene(
                    scene.getSummary(),
                    projectCoreStr,
                    request.getDesignRequest()
            );

            log.info("=== LLM RAW RESPONSE (DESIGN) ===");
            log.info("Raw AI Response: {}", aiResponse);

            return applyDesignToScene(scene, aiResponse, request.getDesignRequest());

        } catch (Exception e) {
            log.error("씬 설계 실패 - fallback 실행", e);
            return applyFallbackDesign(scene, request.getDesignRequest());
        }
    }

    @Override
    public SceneDesignResponse getSceneDesign(Long projectId, Long sceneId, String loginId) {
        Scene scene = sceneRepository.findByIdAndProjectId(sceneId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));

        if (!scene.getProject().getUser().getLoginId().equals(loginId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }

        OptionalElements optionalElements = parseOptionalElements(scene.getOptionalElements());

        return SceneDesignResponse.builder()
                .sceneId(scene.getId())
                .summary(scene.getSummary())
                .optionalElements(optionalElements)
                .imagePrompt(scene.getImagePrompt())
                .videoPrompt(scene.getVideoPrompt())
                .displayText("씬 설계 정보를 조회했습니다.")
                .updatedAt(scene.getUpdatedAt())
                .build();
    }

    @Override
    public SceneDesignResponse regenerateSceneDesign(Long projectId, Long sceneId, String loginId, SceneDesignRegenerateRequest request) {
        Scene scene = sceneRepository.findByIdAndProjectId(sceneId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));

        if (!scene.getProject().getUser().getLoginId().equals(loginId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }

        String variationRequest = (request != null && request.getUserRequest() != null)
                ? request.getUserRequest()
                : "같은 장면을 더 따뜻하고 다른 구도로 다시 설계해주세요.";

        try {
            var planAnalysis = planningService.getLatestPlanAnalysis(projectId);
            String projectCore = buildProjectCoreStr(planAnalysis);

            String aiResponse = openAIService.designScene(scene.getSummary(), projectCore, variationRequest);
            String json = JsonUtils.extractJsonSafely(aiResponse);
            JsonNode root = objectMapper.readTree(json);

            OptionalElements newElements = parseOptionalElementsFromNode(root.path("optionalElements"), variationRequest, scene.getSummary());

            String newImagePrompt = root.path("imagePrompt").asText("");
            String newVideoPrompt = root.path("videoPrompt").asText("");

            if (newImagePrompt.isBlank()) {
                newImagePrompt = generateImagePromptFromSceneAndDesign(scene.getSummary(), newElements);
            }
            if (newVideoPrompt.isBlank()) {
                newVideoPrompt = generateVideoPromptFromSceneAndDesign(scene.getSummary(), newElements);
            }

            scene.setOptionalElements(toJson(newElements));
            scene.setImagePrompt(newImagePrompt);
            scene.setVideoPrompt(newVideoPrompt);

            Scene updatedScene = sceneRepository.save(scene);

            return SceneDesignResponse.builder()
                    .sceneId(updatedScene.getId())
                    .summary(updatedScene.getSummary())
                    .optionalElements(newElements)
                    .imagePrompt(updatedScene.getImagePrompt())
                    .videoPrompt(updatedScene.getVideoPrompt())
                    .displayText("같은 장면을 다른 연출로 다시 추천했습니다.")
                    .updatedAt(updatedScene.getUpdatedAt())
                    .build();

        } catch (Exception e) {
            log.warn("씬 설계 재생성 fallback 실행", e);

            OptionalElements newElements = createOptionalElementsFromRequest(variationRequest, scene.getSummary());
            scene.setOptionalElements(toJson(newElements));
            scene.setImagePrompt(generateImagePromptFromSceneAndDesign(scene.getSummary(), newElements));
            scene.setVideoPrompt(generateVideoPromptFromSceneAndDesign(scene.getSummary(), newElements));

            Scene updatedScene = sceneRepository.save(scene);

            return SceneDesignResponse.builder()
                    .sceneId(updatedScene.getId())
                    .summary(updatedScene.getSummary())
                    .optionalElements(newElements)
                    .imagePrompt(updatedScene.getImagePrompt())
                    .videoPrompt(updatedScene.getVideoPrompt())
                    .displayText("같은 장면을 다른 연출로 다시 추천했습니다. (Fallback)")
                    .updatedAt(updatedScene.getUpdatedAt())
                    .build();
        }
    }

    // ──────────────────────────────────────────
    // SCENE EDIT
    // ──────────────────────────────────────────

    @Override
    public SceneResponse editScene(Long projectId, Long sceneId, String loginId, SceneEditRequest request) {
        Scene scene = sceneRepository.findByIdAndProjectId(sceneId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));

        if (!scene.getProject().getUser().getLoginId().equals(loginId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }

        try {
            String aiResponse = openAIService.editScene(
                    scene.getSummary(),
                    scene.getOptionalElements(),
                    scene.getImagePrompt(),
                    scene.getVideoPrompt(),
                    request.getOptionalElements()
            );

            String json = JsonUtils.extractJsonSafely(aiResponse);
            JsonNode root = objectMapper.readTree(json);

            OptionalElements elements = parseOptionalElementsFromNode(root.path("optionalElements"), request.getOptionalElements(), scene.getSummary());

            String imagePrompt = root.path("imagePrompt").asText("");
            String videoPrompt = root.path("videoPrompt").asText("");

            if (imagePrompt.isBlank()) {
                imagePrompt = generateImagePromptFromSceneAndDesign(scene.getSummary(), elements);
            }
            if (videoPrompt.isBlank()) {
                videoPrompt = generateVideoPromptFromSceneAndDesign(scene.getSummary(), elements);
            }

            scene.setOptionalElements(toJson(elements));
            scene.setImagePrompt(imagePrompt);
            scene.setVideoPrompt(videoPrompt);

            Scene savedScene = sceneRepository.save(scene);

            return SceneResponse.from(
                    savedScene,
                    elements,
                    getRepresentativeImageUrl(savedScene.getId()),
                    getRepresentativeVideoUrl(savedScene.getId())
            );

        } catch (Exception e) {
            log.error("씬 수정 실패 - fallback 실행", e);

            OptionalElements fallback = createOptionalElementsFromRequest(request.getOptionalElements(), scene.getSummary());
            scene.setOptionalElements(toJson(fallback));
            scene.setImagePrompt(generateImagePromptFromSceneAndDesign(scene.getSummary(), fallback));
            scene.setVideoPrompt(generateVideoPromptFromSceneAndDesign(scene.getSummary(), fallback));

            Scene savedScene = sceneRepository.save(scene);

            return SceneResponse.from(
                    savedScene,
                    fallback,
                    getRepresentativeImageUrl(savedScene.getId()),
                    getRepresentativeVideoUrl(savedScene.getId())
            );
        }
    }

    // ──────────────────────────────────────────
    // PRIVATE HELPERS
    // ──────────────────────────────────────────

    private SceneResponse applyDesignToScene(Scene scene, String aiResponse, String designRequest) throws Exception {
        String json = JsonUtils.extractJsonSafely(aiResponse);
        JsonNode root = objectMapper.readTree(json);

        OptionalElements elements = parseOptionalElementsFromNode(root.path("optionalElements"), designRequest, scene.getSummary());

        String imagePrompt = root.path("imagePrompt").asText("");
        String videoPrompt = root.path("videoPrompt").asText("");

        if (imagePrompt.isBlank()) {
            imagePrompt = generateImagePromptFromSceneAndDesign(scene.getSummary(), elements);
        }
        if (videoPrompt.isBlank()) {
            videoPrompt = generateVideoPromptFromSceneAndDesign(scene.getSummary(), elements);
        }

        scene.setOptionalElements(toJson(elements));
        scene.setImagePrompt(imagePrompt);
        scene.setVideoPrompt(videoPrompt);

        Scene savedScene = sceneRepository.save(scene);

        return SceneResponse.from(
                savedScene,
                elements,
                getRepresentativeImageUrl(savedScene.getId()),
                getRepresentativeVideoUrl(savedScene.getId())
        );
    }

    private SceneResponse applyFallbackDesign(Scene scene, String designRequest) {
        OptionalElements fallback = createOptionalElementsFromRequest(designRequest, scene.getSummary());
        scene.setOptionalElements(toJson(fallback));
        scene.setImagePrompt(generateImagePromptFromSceneAndDesign(scene.getSummary(), fallback));
        scene.setVideoPrompt(generateVideoPromptFromSceneAndDesign(scene.getSummary(), fallback));

        Scene savedScene = sceneRepository.save(scene);

        return SceneResponse.from(
                savedScene,
                fallback,
                getRepresentativeImageUrl(savedScene.getId()),
                getRepresentativeVideoUrl(savedScene.getId())
        );
    }

    /**
     * LLM이 optionalElements를 String 또는 Object로 다양하게 반환하므로 안전하게 파싱
     */
    private OptionalElements parseOptionalElementsFromNode(JsonNode node, String designRequest, String sceneSummary) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return createOptionalElementsFromRequest(designRequest, sceneSummary);
        }
        try {
            OptionalElements elements = objectMapper.treeToValue(node, OptionalElements.class);
            return ensureRequiredFields(elements, designRequest, sceneSummary);
        } catch (Exception e) {
            log.warn("OptionalElements 파싱 실패, fallback 생성: {}", e.getMessage());
            return createOptionalElementsFromRequest(designRequest, sceneSummary);
        }
    }

    /**
     * 필수 필드가 비어있으면 채워주는 메서드
     */
    private OptionalElements ensureRequiredFields(OptionalElements elements, String designRequest, String sceneSummary) {
        if (elements == null) {
            return createOptionalElementsFromRequest(designRequest, sceneSummary);
        }

        String req = designRequest == null ? "" : designRequest.toLowerCase();

        if (elements.getAction() == null || elements.getAction().isBlank()) {
            elements.setAction(req.contains("옷") || req.contains("변신")
                    ? "the hamster changes clothes excitedly"
                    : "the hamster moves naturally through the scene");
        }
        if (elements.getCamera() == null || elements.getCamera().isBlank()) {
            if (req.contains("가깝") || req.contains("클로즈")) {
                elements.setCamera("close-up shot");
            } else if (req.contains("멀") || req.contains("와이드")) {
                elements.setCamera("wide shot");
            } else {
                elements.setCamera("medium close-up shot");
            }
        }
        if (elements.getLighting() == null || elements.getLighting().isBlank()) {
            elements.setLighting(req.contains("어둡") || req.contains("밤")
                    ? "low key warm lighting"
                    : "soft warm lighting");
        }
        if (elements.getMood() == null || elements.getMood().isBlank()) {
            elements.setMood(req.contains("활기") || req.contains("역동")
                    ? "bright energetic mood"
                    : "warm cozy mood");
        }
        if (elements.getCameraMotion() == null || elements.getCameraMotion().isBlank()) {
            elements.setCameraMotion("slow push in");
        }
        if (elements.getTimeOfDay() == null || elements.getTimeOfDay().isBlank()) {
            elements.setTimeOfDay("afternoon");
        }
        if (elements.getEffects() == null || elements.getEffects().isEmpty()) {
            elements.setEffects(List.of("soft glow"));
        }

        return elements;
    }

    /**
     * 사용자 요청 텍스트 기반으로 OptionalElements 생성
     */
    private OptionalElements createOptionalElementsFromRequest(String designRequest, String sceneSummary) {
        String req = designRequest == null ? "" : designRequest.toLowerCase();

        String mood;
        String lighting;
        if (req.contains("포근") || req.contains("따뜻")) {
            mood = "warm cozy";
            lighting = "soft warm lighting";
        } else if (req.contains("밝") || req.contains("활기")) {
            mood = "bright energetic";
            lighting = "bright natural lighting";
        } else {
            mood = "gentle warm mood";
            lighting = "balanced soft lighting";
        }

        String camera;
        if (req.contains("가깝") || req.contains("클로즈")) {
            camera = "close-up shot";
        } else if (req.contains("멀") || req.contains("와이드")) {
            camera = "wide shot";
        } else {
            camera = "medium shot";
        }

        String action = (req.contains("옷") || req.contains("변신"))
                ? "the hamster changes clothes and reacts excitedly"
                : "the hamster moves naturally through the scene";

        return OptionalElements.builder()
                .action(action)
                .pose("natural pose")
                .camera(camera)
                .cameraMotion("slow push in")
                .lighting(lighting)
                .mood(mood)
                .timeOfDay("afternoon")
                .effects(List.of("soft glow"))
                .backgroundCharacters("")
                .environmentDetail(sceneSummary != null ? sceneSummary : "")
                .build();
    }

    private String buildProjectCoreStr(com.example.hdb.dto.response.PlanAnalysisResponse planAnalysis) {
        if (planAnalysis == null || planAnalysis.getProjectCore() == null) {
            return "cute hamster trying on different clothes in a cozy wardrobe room";
        }
        var core = planAnalysis.getProjectCore();
        return String.format(
                "purpose=%s, duration=%s, ratio=%s, style=%s, mainCharacter=%s, backgroundWorld=%s, storyLine=%s",
                nullSafe(core.getPurpose()),
                core.getDuration(),
                nullSafe(core.getRatio()),
                nullSafe(core.getStyle()),
                nullSafe(core.getMainCharacter()),
                nullSafe(core.getBackgroundWorld()),
                nullSafe(core.getStoryLine())
        );
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }

    private List<SceneSummaryDto> toSceneSummaryDtoList(List<Scene> scenes) {
        return scenes.stream()
                .map(scene -> SceneSummaryDto.builder()
                        .id(scene.getId())
                        .sceneOrder(scene.getSceneOrder())
                        .summary(scene.getSummary())
                        .status(scene.getStatus().name())
                        .build())
                .collect(Collectors.toList());
    }

    private String getRepresentativeImageUrl(Long sceneId) {
        try {
            return sceneImageRepository.findFirstBySceneIdOrderByImageNumberDesc(sceneId)
                    .map(img -> img.getImageUrl())
                    .orElse(null);
        } catch (Exception e) {
            log.warn("Failed to fetch latest image for scene: {}", sceneId, e);
            return null;
        }
    }

    private String getRepresentativeVideoUrl(Long sceneId) {
        try {
            return sceneVideoRepository.findFirstBySceneIdOrderByCreatedAtDesc(sceneId)
                    .map(video -> video.getVideoUrl())
                    .orElse(null);
        } catch (Exception e) {
            log.warn("Failed to fetch latest video for scene: {}", sceneId, e);
            return null;
        }
    }

    private String toJson(OptionalElements elements) {
        try {
            return elements == null ? "{}" : objectMapper.writeValueAsString(elements);
        } catch (Exception e) {
            return "{\"action\":\"햄스터가 옷을 갈아입는다\",\"camera\":\"medium shot\",\"lighting\":\"warm soft lighting\",\"mood\":\"warm cozy\"}";
        }
    }

    private OptionalElements parseOptionalElements(String json) {
        try {
            if (json == null || json.trim().isEmpty() || "{}".equals(json.trim())) {
                return OptionalElements.builder().build();
            }
            return objectMapper.readValue(json, OptionalElements.class);
        } catch (Exception e) {
            log.warn("Failed to parse optionalElements JSON: {}", json);
            return OptionalElements.builder().build();
        }
    }

    private String generateImagePromptFromSceneAndDesign(String sceneSummary, OptionalElements elements) {
        String action = elements != null && elements.getAction() != null ? elements.getAction() : "the hamster poses naturally";
        String camera = elements != null && elements.getCamera() != null ? elements.getCamera() : "medium close-up shot";
        String lighting = elements != null && elements.getLighting() != null ? elements.getLighting() : "warm soft lighting";
        String mood = elements != null && elements.getMood() != null ? elements.getMood() : "warm cozy";
        return String.format(
                "A cute hamster scene: %s, %s, %s, %s, %s, cinematic composition, detailed, high quality, 4k",
                sceneSummary, action, camera, lighting, mood
        );
    }

    private String generateVideoPromptFromSceneAndDesign(String sceneSummary, OptionalElements elements) {
        String action = elements != null && elements.getAction() != null ? elements.getAction() : "the hamster moves naturally";
        String camera = elements != null && elements.getCamera() != null ? elements.getCamera() : "medium close-up shot";
        String lighting = elements != null && elements.getLighting() != null ? elements.getLighting() : "warm soft lighting";
        String mood = elements != null && elements.getMood() != null ? elements.getMood() : "warm cozy";
        String cameraMotion = elements != null && elements.getCameraMotion() != null ? elements.getCameraMotion() : "slow push in";
        return String.format(
                "Cinematic video of a cute hamster: %s, %s, %s, %s, %s, camera motion: %s, smooth animation, high quality",
                sceneSummary, action, camera, lighting, mood, cameraMotion
        );
    }

    // ──────────────────────────────────────────
    // 하위 호환을 위해 남겨두는 unused 메서드들
    // ──────────────────────────────────────────

    private String extractCoreElementsForSceneGeneration(Project project) {
        try {
            var latestPlan = planningService.getLatestPlan(project.getId());
            if (latestPlan.isPresent()) {
                String planData = latestPlan.get().getPlanData();
                ProjectPlanResponse planResponse = ProjectPlanResponse.fromJson(planData);
                if (planResponse != null && planResponse.getMeta() != null
                        && planResponse.getMeta().getStoryOptions() != null
                        && !planResponse.getMeta().getStoryOptions().isEmpty()) {

                    ProjectPlanResponse.StoryOption selectedOption = planResponse.getMeta().getStoryOptions().get(0);
                    return String.format("%s기획안: %s / %s",
                            selectedOption.getId(),
                            selectedOption.getTitle(),
                            selectedOption.getDescription()
                    );
                }
            }
            return project.getCoreElements() != null
                    ? project.getCoreElements()
                    : String.format("프로젝트 제목: %s", project.getTitle());
        } catch (Exception e) {
            log.error("Error extracting coreElements", e);
            return String.format("프로젝트: %s", project.getTitle());
        }
    }
}