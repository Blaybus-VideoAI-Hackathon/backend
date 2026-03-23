package com.example.hdb.service;

import com.example.hdb.dto.openai.PlanGenerationResponse;
import com.example.hdb.dto.request.PlanCreateRequest;
import com.example.hdb.dto.response.PlanAnalysisResponse;
import com.example.hdb.dto.response.PlanningGenerateResponse;
import com.example.hdb.dto.response.PromptGenerateResponse;
import com.example.hdb.dto.response.ProjectPlanResponse;
import com.example.hdb.entity.ProjectPlan;
import com.example.hdb.entity.ProjectStatus;
import com.example.hdb.entity.Scene;
import com.example.hdb.exception.BusinessException;
import com.example.hdb.exception.ErrorCode;
import com.example.hdb.repository.ProjectPlanRepository;
import com.example.hdb.repository.ProjectRepository;
import com.example.hdb.repository.SceneRepository;
import com.example.hdb.repository.UserRepository;
import com.example.hdb.dto.response.PlanningSummaryResponse;
import com.example.hdb.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
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
    private final SceneRepository sceneRepository;
    private final ObjectMapper objectMapper;
    private final OpenAIService openAIService;
    
    /**
     * 프로젝트 기획 생성 (실제 LLM 호출)
     */
    public PlanningGenerateResponse generatePlanning(Long projectId, String userPrompt, String loginId) {
        log.info("=== Planning Generation Started ===");
        log.info("Project ID: {}", projectId);
        log.info("User Prompt: {}", userPrompt);
        log.info("Login ID: {}", loginId);
        
        // 프로젝트 정보 조회
        var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        
        log.info("Project found - Purpose: {}, Duration: {}, Ratio: {}, Style: {}", 
                project.getPurpose(), project.getDuration(), project.getRatio(), project.getStyle());
        
        try {
            // OpenAI로 기획 생성
            String aiResponse = openAIService.generatePlanningWithSummary(
                    userPrompt, 
                    project.getPurpose(), 
                    project.getDuration(), 
                    project.getRatio(), 
                    project.getStyle()
            );
            log.info("OpenAI 기획 생성 응답 수신: {}", aiResponse);
            
            // JSON 추출 및 파싱
            String json = JsonUtils.extractJsonSafely(aiResponse);
            log.info("추출된 JSON: {}", json);
            
            // OpenAI 응답을 PlanningGenerateResponse로 파싱
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);
            
            JsonNode plansNode = root.get("plans");
            
            if (plansNode == null) {
                log.error("Invalid JSON structure - missing plans");
                throw new BusinessException(ErrorCode.LLM_GENERATION_FAILED);
            }
            
            // plans 파싱
            List<PlanningGenerateResponse.Plan> plans = mapper.convertValue(
                plansNode, new TypeReference<List<PlanningGenerateResponse.Plan>>() {}
            );
            
            // 선택된 기획안이 없으므로 null로 설정
            Integer selectedPlanId = project.getSelectedPlanId();
            
            log.info("Successfully parsed AI response - plans count: {}, selectedPlanId: {}", 
                    plans.size(), selectedPlanId);
            
            return PlanningGenerateResponse.builder()
                    .projectId(projectId)
                    .selectedPlanId(selectedPlanId)  // 선택된 기획안 없으면 null
                    .plans(plans)
                    .build();
            
        } catch (Exception e) {
            log.error("기획 생성 실패 - fallback 실행", e);
            
            // fallback 생성 (placeholder 금지, 구체적인 내용)
            List<PlanningGenerateResponse.Plan> fallbackPlans = java.util.List.of(
                PlanningGenerateResponse.Plan.builder()
                        .planId(1)
                        .title("기본 기획안")
                        .focus("사용자 요청 기반")
                        .displayText("사용자의 요청을 바탕으로 생성된 기본 기획안입니다.")
                        .recommendationReason("사용자 요청에 가장 적합한 기본 구성")
                        .strengths(java.util.List.of("요청 반영", "기본 구성"))
                        .targetMood("사용자 요청 기반 분위기")
                        .targetUseCase("사용자 요청 기반 활용")
                        .storyLine("사용자 요청 기반 스토리라인")
                        .coreElements(PlanningGenerateResponse.CoreElements.builder()
                                .purpose(project.getPurpose())
                                .duration(project.getDuration())
                                .ratio(project.getRatio())
                                .style(project.getStyle())
                                .mainCharacter("사용자 요청에 맞는 주인공")
                                .subCharacters(java.util.List.of("보조 캐릭터", "배경 캐릭터"))
                                .backgroundWorld("사용자 요청에 맞는 배경 세계관")
                                .storyFlow("도입 → 전개 → 클라이맥스 → 결말")
                                .storyLine("사용자 요청 기반 스토리라인")
                                .build())
                        .build()
            );
            
            log.warn("Fallback planning generated for projectId: {}", projectId);
            
            return PlanningGenerateResponse.builder()
                    .projectId(projectId)
                    .selectedPlanId(null)
                    .plans(fallbackPlans)
                    .build();
        }
    }

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
        
        // 프로젝트 조회
        var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        
        // 선택된 기획안 저장
        project.setSelectedPlanId(selectedPlanId);
        projectRepository.save(project);
        
        log.info("Plan selection completed - Project: {}, SelectedPlan: {}", projectId, selectedPlanId);
    }

    /**
     * 프로젝트 기획 요약 조회 (선택된 기획안 기준)
     */
    public PlanningSummaryResponse getPlanningSummary(Long projectId) {
        log.info("=== Planning Summary Started ===");
        log.info("Project ID: {}", projectId);
        
        // 프로젝트 조회
        var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        
        // 최신 기획안 조회
        Optional<ProjectPlan> latestPlan = getLatestPlan(projectId);
        if (latestPlan.isEmpty()) {
            // 기획안이 없으면 프로젝트 기본 정보만 반환
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
        
        // 선택된 기획안 ID 확인
        Integer selectedPlanId = project.getSelectedPlanId();
        if (selectedPlanId == null) {
            // 선택된 기획안이 없으면 1안 기준으로 반환
            selectedPlanId = 1;
            log.info("No selected plan found, using default plan 1");
        }
        
        log.info("Using selected plan ID: {}", selectedPlanId);
        
        try {
            // 기획안 데이터 파싱
            String planData = latestPlan.get().getPlanData();
            log.info("Parsing plan data for summary: {}", planData);
            
            String json = JsonUtils.extractJsonSafely(planData);
            ObjectMapper mapper = new ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode plansJson = mapper.readTree(json);
            com.fasterxml.jackson.databind.JsonNode plansArray = plansJson.get("plans");
            
            if (plansArray != null && plansArray.isArray() && plansArray.size() > 0) {
                // 선택된 기획안 찾기 (selectedPlanId 기준)
                com.fasterxml.jackson.databind.JsonNode selectedPlanJson = plansArray.get(selectedPlanId - 1);
                
                if (selectedPlanJson != null) {
                    com.fasterxml.jackson.databind.JsonNode coreElements = selectedPlanJson.get("coreElements");
                    
                    PlanningSummaryResponse response = PlanningSummaryResponse.builder()
                            .projectId(projectId)
                            .selectedPlanId(selectedPlanId)
                            .selectedPlanTitle(selectedPlanJson.get("title").asText())
                            .purpose(project.getPurpose())  // 프로젝트 공통 정보
                            .duration(project.getDuration())  // 프로젝트 공통 정보
                            .ratio(project.getRatio())  // 프로젝트 공통 정보
                            .style(project.getStyle())  // 프로젝트 공통 정보
                            .mainCharacter(coreElements != null ? coreElements.get("mainCharacter").asText() : null)
                            .subCharacters(coreElements != null && coreElements.get("subCharacters").isArray() ? 
                                    mapper.convertValue(coreElements.get("subCharacters"), List.class) : null)
                            .backgroundWorld(coreElements != null ? coreElements.get("backgroundWorld").asText() : null)
                            .storyFlow(coreElements != null ? coreElements.get("storyFlow").asText() : null)
                            .storyLine(coreElements != null ? coreElements.get("storyLine").asText() : null)
                            .build();
                    
                    log.info("Planning summary generated successfully - using selected plan: {}", selectedPlanId);
                    return response;
                }
            }
            
            // 파싱 실패 시 기본 응답
            return createDefaultPlanningSummary(projectId, project);
            
        } catch (Exception e) {
            log.error("Failed to generate planning summary", e);
            return createDefaultPlanningSummary(projectId, project);
        }
    }

    /**
     * 선택된 기획안 분석
     */
    public PlanAnalysisResponse analyzeSelectedPlan(Long projectId, Integer planId, String loginId) {
        log.info("=== Plan Analysis Started ===");
        log.info("Project ID: {}, Plan ID: {}, User: {}", projectId, planId, loginId);
        
        // 프로젝트 및 기획안 조회
        var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        
        Optional<ProjectPlan> latestPlan = getLatestPlan(projectId);
        if (latestPlan.isEmpty()) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
        
        try {
            // 실제 저장된 plan_data JSON 구조 로그 출력
            String planData = latestPlan.get().getPlanData();
            log.info("Latest plan_data JSON: {}", planData);
            
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(JsonUtils.extractJsonSafely(planData));
            
            // 1순위: plans 배열
            JsonNode plansArray = root.path("plans");
            if (!plansArray.isArray() || plansArray.isEmpty()) {
                // 2순위: 구버전 meta.storyOptions fallback
                plansArray = root.path("meta").path("storyOptions");
            }
            
            if (!plansArray.isArray() || plansArray.isEmpty()) {
                log.error("No plans/storyOptions found in plan_data: {}", planData);
                throw new BusinessException(ErrorCode.LLM_GENERATION_FAILED);
            }
            
            // planId 유효성 확인 (1-based)
            if (planId < 1 || planId > plansArray.size()) {
                log.error("Invalid planId: {}, plans size: {}", planId, plansArray.size());
                throw new BusinessException(ErrorCode.LLM_GENERATION_FAILED);
            }
            
            // 선택된 기획안 찾기
            JsonNode selectedPlanJson = plansArray.get(planId - 1);
            log.info("Selected plan JSON: {}", selectedPlanJson);
            
            // coreElements 가져오기 (null-safe)
            JsonNode coreElements = selectedPlanJson.path("coreElements");
            log.info("Core elements JSON: {}", coreElements);
            
            // 필드명 호환 처리로 storyLine 추출 (null-safe)
            String storyLine = selectedPlanJson.path("storyLine").asText(
                coreElements.path("storyLine").asText("")
            );
            
            if (storyLine.isEmpty()) {
                log.error("StoryLine is empty - selectedPlan: {}, coreElements: {}", selectedPlanJson, coreElements);
                throw new BusinessException(ErrorCode.LLM_GENERATION_FAILED);
            }
            
            log.info("Extracted storyLine: {}", storyLine);
            
            // 기타 핵심요소 추출 (null-safe, 필드명 호환 처리)
            String mainCharacter = coreElements.path("mainCharacter").asText("");
            String backgroundWorld = coreElements.path("backgroundWorld").asText(
                coreElements.path("background").asText("")
            );
            String storyFlow = coreElements.path("storyFlow").asText("");
            
            log.info("Extracted elements - mainCharacter: {}, backgroundWorld: {}, storyFlow: {}", 
                    mainCharacter, backgroundWorld, storyFlow);
            
            // OpenAI로 기획안 분석
            String aiResponse = openAIService.analyzeSelectedPlan(
                    storyLine,
                    project.getPurpose(),
                    project.getDuration(),
                    project.getRatio(),
                    project.getStyle()
            );
            log.info("OpenAI 기획안 분석 응답 수신: {}", aiResponse);
            
            // JSON 추출 및 파싱
            String json = JsonUtils.extractJsonSafely(aiResponse);
            JsonNode analysisRoot = mapper.readTree(json);
            
            JsonNode projectCoreNode = analysisRoot.path("projectCore");
            JsonNode scenePlanNode = analysisRoot.path("scenePlan");
            
            if (projectCoreNode.isMissingNode() || scenePlanNode.isMissingNode()) {
                log.error("Invalid analysis JSON structure - missing projectCore or scenePlan. Response: {}", aiResponse);
                throw new BusinessException(ErrorCode.LLM_GENERATION_FAILED);
            }
            
            // 프로젝트 핵심요소 파싱
            PlanAnalysisResponse.ProjectCore projectCore = mapper.treeToValue(
                projectCoreNode, PlanAnalysisResponse.ProjectCore.class
            );
            
            // 씬 플랜 파싱
            PlanAnalysisResponse.ScenePlan scenePlan = mapper.treeToValue(
                scenePlanNode, PlanAnalysisResponse.ScenePlan.class
            );
            
            log.info("Successfully analyzed plan - scenes count: {}", 
                    scenePlan.getScenes() != null ? scenePlan.getScenes().size() : 0);
            
            return PlanAnalysisResponse.builder()
                    .projectId(projectId)
                    .selectedPlanId(planId)
                    .projectCore(projectCore)
                    .scenePlan(scenePlan)
                    .build();
                    
        } catch (Exception e) {
            log.error("기획안 분석 실패 - planId: {}, error: {}", planId, e.getMessage(), e);
            
            // fallback 생성
            PlanAnalysisResponse.ProjectCore fallbackCore = PlanAnalysisResponse.ProjectCore.builder()
                    .purpose(project.getPurpose())
                    .duration(project.getDuration())
                    .ratio(project.getRatio())
                    .style(project.getStyle())
                    .mainCharacter("분석된 주요 캐릭터")
                    .subCharacters(java.util.List.of("보조 캐릭터"))
                    .backgroundWorld("분석된 배경 세계관")
                    .storyFlow("분석된 스토리 흐름")
                    .storyLine("선택된 기획안의 스토리라인")
                    .build();
            
            List<PlanAnalysisResponse.SceneInfo> fallbackScenes = java.util.List.of(
                PlanAnalysisResponse.SceneInfo.builder()
                        .sceneOrder(1)
                        .summary("분석된 첫 번째 장면")
                        .sceneGoal("도입")
                        .emotionBeat("기대감")
                        .estimatedDuration(5)
                        .build(),
                PlanAnalysisResponse.SceneInfo.builder()
                        .sceneOrder(2)
                        .summary("분석된 두 번째 장면")
                        .sceneGoal("전개")
                        .emotionBeat("흥미")
                        .estimatedDuration(10)
                        .build(),
                PlanAnalysisResponse.SceneInfo.builder()
                        .sceneOrder(3)
                        .summary("분석된 세 번째 장면")
                        .sceneGoal("결말")
                        .emotionBeat("만족감")
                        .estimatedDuration(5)
                        .build()
            );
            
            PlanAnalysisResponse.ScenePlan fallbackScenePlan = PlanAnalysisResponse.ScenePlan.builder()
                    .recommendedSceneCount(fallbackScenes.size())
                    .scenes(fallbackScenes)
                    .build();
            
            log.warn("Fallback plan analysis generated for projectId: {}, planId: {}", projectId, planId);
            
            return PlanAnalysisResponse.builder()
                    .projectId(projectId)
                    .selectedPlanId(planId)
                    .projectCore(fallbackCore)
                    .scenePlan(fallbackScenePlan)
                    .build();
        }
    }

    /**
     * 프롬프트 생성
     */
    public PromptGenerateResponse generatePrompt(Long projectId, Long sceneId, String loginId) {
        log.info("=== Prompt Generation Started ===");
        log.info("Project ID: {}, Scene ID: {}, User: {}", projectId, sceneId, loginId);
        
        try {
            // 씬 정보 조회
            var scene = sceneRepository.findById(sceneId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));
            
            // 프로젝트 정보 조회
            var project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
            
            // 선택된 기획안 분석 결과 조회 (projectCore)
            var planAnalysis = getLatestPlanAnalysis(projectId);
            if (planAnalysis == null) {
                throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
            }
            
            // 프로젝트 핵심요소 문자열화
            String projectCoreStr = String.format(
                "purpose: %s, duration: %d, ratio: %s, style: %s, mainCharacter: %s, backgroundWorld: %s",
                planAnalysis.getProjectCore().getPurpose(),
                planAnalysis.getProjectCore().getDuration(),
                planAnalysis.getProjectCore().getRatio(),
                planAnalysis.getProjectCore().getStyle(),
                planAnalysis.getProjectCore().getMainCharacter(),
                planAnalysis.getProjectCore().getBackgroundWorld()
            );
            
            // 씬 부가요소 조회 (없으면 기본값)
            String optionalElementsStr = "기본 연출 요소";
            if (scene.getOptionalElements() != null) {
                optionalElementsStr = scene.getOptionalElements().toString();
            }
            
            // OpenAI로 최종 프롬프트 생성
            String aiResponse = openAIService.generateFinalPrompts(
                    scene.getSummary(),
                    projectCoreStr,
                    optionalElementsStr
            );
            log.info("OpenAI 프롬프트 생성 응답 수신: {}", aiResponse);
            
            // JSON 파싱
            String json = JsonUtils.extractJsonSafely(aiResponse);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);
            
            String imagePrompt = root.get("imagePrompt").asText();
            String videoPrompt = root.get("videoPrompt").asText();
            
            log.info("Successfully generated prompts - sceneId: {}", sceneId);
            
            return PromptGenerateResponse.builder()
                    .sceneId(sceneId)
                    .imagePrompt(imagePrompt)
                    .videoPrompt(videoPrompt)
                    .build();
                    
        } catch (Exception e) {
            log.error("프롬프트 생성 실패 - fallback 실행", e);
            
            // fallback 생성
            return PromptGenerateResponse.builder()
                    .sceneId(sceneId)
                    .imagePrompt("Fallback 이미지 프롬프트: " + sceneId + "번 장면")
                    .videoPrompt("Fallback 영상 프롬프트: " + sceneId + "번 장면")
                    .build();
        }
    }
    
    // 최신 기획안 분석 결과 조회 헬퍼 메서드
    private PlanAnalysisResponse getLatestPlanAnalysis(Long projectId) {
        // TODO: 실제로는 분석 결과를 저장하고 조회하는 로직 필요
        // 임시로 null 반환
        return null;
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

        // OpenAI로 기획 생성 (프로젝트 정보 전달)
        log.info("OpenAI 기획 생성 시작 - 사용자 입력: {}", request.getUserPrompt());
        log.info("프로젝트 정보 - 목적: {}, 길이: {}초, 비율: {}, 스타일: {}", 
                project.getPurpose(), project.getDuration(), project.getRatio(), project.getStyle());
        
        try {
            // OpenAI 호출 (프로젝트 정보 전달)
            String aiResponse = openAIService.generatePlanningWithSummary(
                    request.getUserPrompt(), 
                    project.getPurpose(), 
                    project.getDuration(), 
                    project.getRatio(), 
                    project.getStyle()
            );
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
