package com.example.hdb.service.impl;

import com.example.hdb.dto.request.SceneCreateRequest;
import com.example.hdb.dto.request.SceneDesignRegenerateRequest;
import com.example.hdb.dto.request.SceneDesignRequest;
import com.example.hdb.dto.request.SceneEditRequest;
import com.example.hdb.dto.request.SceneGenerationRequest;
import com.example.hdb.dto.request.SceneUpdateRequest;
import com.example.hdb.dto.response.SceneDesignResponse;
import com.example.hdb.dto.response.SceneResponse;
import com.example.hdb.dto.common.OptionalElements;
import com.example.hdb.dto.openai.SceneGenerationResponse;
import com.example.hdb.entity.Project;
import com.example.hdb.entity.Scene;
import com.example.hdb.entity.SceneImage;
import com.example.hdb.entity.SceneVideo;
import com.example.hdb.exception.BusinessException;
import com.example.hdb.exception.ErrorCode;
import com.example.hdb.repository.ProjectRepository;
import com.example.hdb.repository.SceneRepository;
import com.example.hdb.repository.SceneImageRepository;
import com.example.hdb.repository.SceneVideoRepository;
import com.example.hdb.service.SceneService;
import com.example.hdb.service.OpenAIService;
import com.example.hdb.util.JsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class SceneServiceImpl implements SceneService {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SceneServiceImpl.class);
    
    private final SceneRepository sceneRepository;
    private final ProjectRepository projectRepository;
    private final ObjectMapper objectMapper;
    private final OpenAIService openAIService;
    private final SceneImageRepository sceneImageRepository;
    private final SceneVideoRepository sceneVideoRepository;
    
    @Override
    public SceneResponse createScene(SceneCreateRequest request) {
        log.info("Creating scene with projectId: {}, sceneOrder: {}", request.getProjectId(), request.getSceneOrder());
        
        // 프로젝트 존재 확인
        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        
        // sceneOrder 중복 확인
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
        
        Scene savedScene = sceneRepository.save(scene);
        log.info("Scene created successfully with id: {}", savedScene.getId());
        
        return SceneResponse.from(savedScene);
    }
    
    @Override
    public SceneResponse updateScene(Long sceneId, SceneUpdateRequest request) {
        log.info("Updating scene with id: {}", sceneId);
        
        Scene scene = sceneRepository.findById(sceneId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));
        
        scene.setSummary(request.getSummary());
        scene.setOptionalElements(request.getOptionalElements());
        scene.setImagePrompt(request.getImagePrompt());
        scene.setVideoPrompt(request.getVideoPrompt());
        
        Scene updatedScene = sceneRepository.save(scene);
        log.info("Scene updated successfully with id: {}", updatedScene.getId());
        
        return SceneResponse.from(updatedScene);
    }
    
    @Override
    public SceneResponse updateSceneAndCheckPermission(Long sceneId, Long userId, SceneUpdateRequest request) {
        log.info("Updating scene with id: {} for userId: {}", sceneId, userId);
        
        Scene scene = sceneRepository.findByIdAndProjectUserId(sceneId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));
        
        scene.setSummary(request.getSummary());
        scene.setOptionalElements(request.getOptionalElements());
        scene.setImagePrompt(request.getImagePrompt());
        scene.setVideoPrompt(request.getVideoPrompt());
        
        Scene updatedScene = sceneRepository.save(scene);
        log.info("Scene updated successfully with id: {} for userId: {}", updatedScene.getId(), userId);
        
        // 대표 URL 계산
        String imageUrl = getRepresentativeImageUrl(updatedScene.getId());
        String videoUrl = getRepresentativeVideoUrl(updatedScene.getId());
        
        return SceneResponse.from(updatedScene, null, imageUrl, videoUrl);
    }
    
    @Override
    public List<SceneResponse> getScenesByProjectId(Long projectId) {
        log.info("Getting scenes for projectId: {}", projectId);
        
        // 프로젝트 존재 확인
        if (!projectRepository.existsById(projectId)) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
        
        List<Scene> scenes = sceneRepository.findByProjectIdOrderBySceneOrderAsc(projectId);
        
        return scenes.stream()
                .map(scene -> {
                    // 대표 URL 계산
                    String imageUrl = getRepresentativeImageUrl(scene.getId());
                    String videoUrl = getRepresentativeVideoUrl(scene.getId());
                    return SceneResponse.from(scene, null, imageUrl, videoUrl);
                })
                .collect(Collectors.toList());
    }
    
    // ========== 신규 메서드 (Scene 기능 확장) ==========
    
    @Override
    public List<SceneResponse> generateScenes(Long projectId, String sceneGenerationRequest) {
        log.info("Generating scenes for projectId: {}, request: {}", projectId, sceneGenerationRequest);
        
        // 프로젝트 존재 확인
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        
        // 중복 생성 방지: 기존 scene이 있으면 삭제 후 재생성
        List<Scene> existingScenes = sceneRepository.findByProjectId(projectId);
        if (!existingScenes.isEmpty()) {
            log.info("Deleting {} existing scenes for projectId: {}", existingScenes.size(), projectId);
            sceneRepository.deleteByProjectId(projectId);
        }
        
        try {
            // OpenAI로 씬 생성
            log.info("OpenAI 호출 - projectTitle: {}, coreElements: {}, request: {}", 
                    project.getTitle(), project.getCoreElements(), sceneGenerationRequest);
            
            String aiResponse = openAIService.generateScenesFromProject(
                    project.getTitle(), 
                    project.getCoreElements(), 
                    sceneGenerationRequest
            );
            log.info("OpenAI 씬 생성 응답 수신: {}", aiResponse);
            
            // JSON 추출
            String json = JsonUtils.extractJsonSafely(aiResponse);
            log.info("추출된 JSON: {}", json);
            
            // JSON 파싱
            SceneGenerationResponse sceneResponse = objectMapper.readValue(json, SceneGenerationResponse.class);
            log.info("씬 파싱 완료 - 생성된 씬 수: {}", sceneResponse.getScenes().size());
            
            // 방어 코드: 씬 개수 제한 (2~5개)
            List<SceneGenerationResponse.SceneData> scenes = sceneResponse.getScenes();
            if (scenes.size() > 5) {
                log.warn("GPT가 5개 초과 씬 생성: {}개 -> 5개로 제한", scenes.size());
                scenes = scenes.subList(0, 5);
            }
            if (scenes.size() < 2) {
                log.error("GPT가 2개 미만 씬 생성: {}개", scenes.size());
                throw new BusinessException(ErrorCode.LLM_GENERATION_FAILED);
            }
            
            log.info("최종 씬 수: {}", scenes.size());
            
            // Scene 엔티티로 변환
            List<Scene> generatedScenes = scenes.stream()
                    .map(sceneData -> Scene.builder()
                            .project(project)
                            .sceneOrder(sceneData.getSceneOrder())
                            .summary(sceneData.getSummary())
                            .optionalElements(createOptionalElementsJson(sceneData.getOptionalElements()))
                            .imagePrompt(sceneData.getImagePrompt())
                            .videoPrompt(sceneData.getVideoPrompt())
                            .status(com.example.hdb.enums.SceneStatus.PENDING)
                            .build())
                    .collect(Collectors.toList());
            
            // DB에 저장
            List<Scene> savedScenes = sceneRepository.saveAll(generatedScenes);
            
            log.info("Generated {} scenes for projectId: {}", savedScenes.size(), projectId);
            
            return savedScenes.stream()
                    .map(scene -> {
                        // Service 레이어에서 JSON 파싱 수행
                        OptionalElements optionalElementsObj = parseOptionalElements(scene.getOptionalElements());
                        // 대표 URL 계산
                        String imageUrl = getRepresentativeImageUrl(scene.getId());
                        String videoUrl = getRepresentativeVideoUrl(scene.getId());
                        return SceneResponse.from(scene, optionalElementsObj, imageUrl, videoUrl);
                    })
                    .collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("씬 생성 실패 - fallback 실행. 원본 요청: {}", sceneGenerationRequest, e);
            
            // fallback: 프로젝트 정보 기반 기본 scene 2개 생성
            String projectTitle = project.getTitle();
            String coreElements = project.getCoreElements();
            
            List<Scene> fallbackScenes = List.of(
                Scene.builder()
                        .project(project)
                        .sceneOrder(1)
                        .summary(String.format("%s의 첫 번째 장면", projectTitle))
                        .optionalElements(createOptionalElementsJson("도입", "준비"))
                        .imagePrompt(String.format("First scene of %s with %s, introduction and preparation mood", projectTitle, coreElements))
                        .videoPrompt(String.format("Opening scene showing %s with %s, setting up the atmosphere", projectTitle, coreElements))
                        .status(com.example.hdb.enums.SceneStatus.PENDING)
                        .build(),
                Scene.builder()
                        .project(project)
                        .sceneOrder(2)
                        .summary(String.format("%s의 두 번째 장면", projectTitle))
                        .optionalElements(createOptionalElementsJson("전개", "활동"))
                        .imagePrompt(String.format("Second scene of %s with %s, development and activity mood", projectTitle, coreElements))
                        .videoPrompt(String.format("Development scene showing %s with %s, main action begins", projectTitle, coreElements))
                        .status(com.example.hdb.enums.SceneStatus.PENDING)
                        .build()
            );
            
            // DB에 저장
            List<Scene> savedScenes = sceneRepository.saveAll(fallbackScenes);
            
            log.info("Fallback 씬 생성 완료 - {}개 저장", savedScenes.size());
            
            return savedScenes.stream()
                    .map(scene -> {
                        OptionalElements optionalElementsObj = parseOptionalElements(scene.getOptionalElements());
                        // 대표 URL 계산
                        String imageUrl = getRepresentativeImageUrl(scene.getId());
                        String videoUrl = getRepresentativeVideoUrl(scene.getId());
                        return SceneResponse.from(scene, optionalElementsObj, imageUrl, videoUrl);
                    })
                    .collect(Collectors.toList());
        }
    }
    
    @Override
    public SceneResponse designScene(Long projectId, Long sceneId, String loginId, SceneDesignRequest request) {
        log.info("Designing scene: {} for project: {}, loginId: {}, request: {}", sceneId, projectId, loginId, request.getDesignRequest());
        
        // 권한 체크: scene이 해당 project에 속하는지 확인
        Scene scene = sceneRepository.findByIdAndProjectId(sceneId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));
        
        // 권한 체크: project가 해당 사용자 소유인지 확인
        if (!scene.getProject().getUser().getLoginId().equals(loginId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        
        try {
            // OpenAI로 씬 설계
            String aiResponse = openAIService.designScene(scene.getSummary(), request.getDesignRequest());
            log.info("OpenAI 씬 설계 응답 수신: {}", aiResponse);
            
            // JSON 추출
            String json = JsonUtils.extractJsonSafely(aiResponse);
            log.info("추출된 JSON: {}", json);
            
            // JSON 파싱
            SceneDesignResponse designResponse = objectMapper.readValue(json, SceneDesignResponse.class);
            
            // 씬 업데이트 - SceneDesignResponse의 필드를 직접 사용
            scene.setOptionalElements(objectMapper.writeValueAsString(designResponse.getOptionalElements()));
            scene.setImagePrompt(designResponse.getImagePrompt());
            scene.setVideoPrompt(designResponse.getVideoPrompt());
            
            Scene savedScene = sceneRepository.save(scene);
            
            log.info("Scene designed successfully: {}", savedScene.getId());
            
            // Service 레이어에서 JSON 파싱 수행
            OptionalElements optionalElementsObj = parseOptionalElements(savedScene.getOptionalElements());
            // 대표 URL 계산
            String imageUrl = getRepresentativeImageUrl(savedScene.getId());
            String videoUrl = getRepresentativeVideoUrl(savedScene.getId());
            return SceneResponse.from(savedScene, optionalElementsObj, imageUrl, videoUrl);
            
        } catch (Exception e) {
            log.error("씬 설계 실패 - fallback 실행", e);
            
            // fallback: 기존 데이터 유지 + 최소한의 prompt만 생성
            scene.setOptionalElements(createOptionalElementsJson("기본 설계", "표준"));
            scene.setImagePrompt("A standard scene with basic lighting and composition");
            scene.setVideoPrompt("A standard video scene with basic camera work");
            
            Scene savedScene = sceneRepository.save(scene);
            
            log.info("Fallback 씬 설계 완료 - sceneId: {}", savedScene.getId());
            
            OptionalElements optionalElementsObj = parseOptionalElements(savedScene.getOptionalElements());
            // 대표 URL 계산
            String imageUrl = getRepresentativeImageUrl(savedScene.getId());
            String videoUrl = getRepresentativeVideoUrl(savedScene.getId());
            return SceneResponse.from(savedScene, optionalElementsObj, imageUrl, videoUrl);
        }
    }
    
    @Override
    public SceneResponse editScene(Long projectId, Long sceneId, String loginId, SceneEditRequest request) {
        log.info("Editing scene: {} for project: {}, loginId: {}, optionalElements: {}", sceneId, projectId, loginId, request.getOptionalElements());
        
        // 권한 체크: scene이 해당 project에 속하는지 확인
        Scene scene = sceneRepository.findByIdAndProjectId(sceneId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));
        
        // 권한 체크: project가 해당 사용자 소유인지 확인
        if (!scene.getProject().getUser().getLoginId().equals(loginId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        
        try {
            // OpenAI로 씬 수정
            String aiResponse = openAIService.editScene(
                    scene.getSummary(),
                    scene.getOptionalElements(),
                    scene.getImagePrompt(),
                    scene.getVideoPrompt(),
                    request.getOptionalElements() // 수정 요청을 editRequest로 전달
            );
            log.info("OpenAI 씬 수정 응답 수신: {}", aiResponse);
            
            // JSON 추출
            String json = JsonUtils.extractJsonSafely(aiResponse);
            log.info("추출된 JSON: {}", json);
            
            // JSON 파싱
            SceneDesignResponse editResponse = objectMapper.readValue(json, SceneDesignResponse.class);
            
            // 씬 업데이트 - SceneDesignResponse의 필드를 직접 사용
            scene.setOptionalElements(objectMapper.writeValueAsString(editResponse.getOptionalElements()));
            scene.setImagePrompt(editResponse.getImagePrompt());
            scene.setVideoPrompt(editResponse.getVideoPrompt());
            
            Scene savedScene = sceneRepository.save(scene);
            
            log.info("Scene edited successfully: {}", savedScene.getId());
            
            // Service 레이어에서 JSON 파싱 수행
            OptionalElements optionalElementsObj = parseOptionalElements(savedScene.getOptionalElements());
            // 대표 URL 계산
            String imageUrl = getRepresentativeImageUrl(savedScene.getId());
            String videoUrl = getRepresentativeVideoUrl(savedScene.getId());
            return SceneResponse.from(savedScene, optionalElementsObj, imageUrl, videoUrl);
            
        } catch (Exception e) {
            log.error("씬 수정 실패 - fallback 실행", e);
            
            // fallback: 기존 데이터 유지 + 최소한의 수정만 적용
            if (request.getOptionalElements() != null) {
                scene.setOptionalElements(createOptionalElementsJson("수정됨", "기본"));
            }
            scene.setImagePrompt("A modified scene with updated elements");
            scene.setVideoPrompt("A modified video scene with updated composition");
            
            Scene savedScene = sceneRepository.save(scene);
            
            log.info("Fallback 씬 수정 완료 - sceneId: {}", savedScene.getId());
            
            OptionalElements optionalElementsObj = parseOptionalElements(savedScene.getOptionalElements());
            // 대표 URL 계산
            String imageUrl = getRepresentativeImageUrl(savedScene.getId());
            String videoUrl = getRepresentativeVideoUrl(savedScene.getId());
            return SceneResponse.from(savedScene, optionalElementsObj, imageUrl, videoUrl);
        }
    }
    
    // ========== JSON 처리 헬퍼 메서드 ==========
    
    /**
     * 대표 이미지 URL 조회
     */
    private String getRepresentativeImageUrl(Long sceneId) {
        try {
            Optional<com.example.hdb.entity.SceneImage> latestImage = sceneImageRepository.findFirstBySceneIdOrderByImageNumberDesc(sceneId);
            if (latestImage.isPresent() && latestImage.get().getImageUrl() != null) {
                return latestImage.get().getImageUrl();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch latest image for scene: {}", sceneId, e);
        }
        return null;
    }
    
    /**
     * 대표 영상 URL 조회
     */
    private String getRepresentativeVideoUrl(Long sceneId) {
        try {
            Optional<com.example.hdb.entity.SceneVideo> latestVideo = sceneVideoRepository.findFirstBySceneIdOrderByCreatedAtDesc(sceneId);
            if (latestVideo.isPresent() && latestVideo.get().getVideoUrl() != null) {
                return latestVideo.get().getVideoUrl();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch latest video for scene: {}", sceneId, e);
        }
        return null;
    }
    
    /**
     * OptionalElements 객체를 JSON 문자열로 변환
     */
    private String createOptionalElementsJson(String action, String mood) {
        try {
            OptionalElements optionalElements = OptionalElements.builder()
                    .action(action)
                    .mood(mood)
                    .build();
            
            return objectMapper.writeValueAsString(optionalElements);
        } catch (Exception e) {
            log.error("Failed to create optionalElements JSON", e);
            // 실패 시 기본 JSON 문자열 반환
            return String.format("{\"action\": \"%s\", \"mood\": \"%s\"}", action, mood);
        }
    }
    
    /**
     * OptionalElements 객체를 JSON 문자열로 변환 (오버로드)
     */
    private String createOptionalElementsJson(OptionalElements optionalElements) {
        try {
            if (optionalElements == null) {
                return "{}";
            }
            return objectMapper.writeValueAsString(optionalElements);
        } catch (Exception e) {
            log.error("Failed to create optionalElements JSON", e);
            // 실패 시 기본 JSON 문자열 반환
            return "{}";
        }
    }
    
    @Override
    public void deleteScene(Long projectId, Long sceneId, String loginId) {
        log.info("Deleting scene: sceneId={}, projectId={}, loginId={}", sceneId, projectId, loginId);
        
        try {
            // 권한 체크: scene이 해당 project에 속하는지 확인
            Scene scene = sceneRepository.findByIdAndProjectId(sceneId, projectId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));
            
            // 권한 체크: project가 해당 사용자 소유인지 확인
            if (!scene.getProject().getUser().getLoginId().equals(loginId)) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
            }
            
            // 관련 이미지 삭제
            sceneImageRepository.deleteAllBySceneId(sceneId);
            log.info("Deleted scene images for sceneId: {}", sceneId);
            
            // 관련 영상 삭제
            sceneVideoRepository.deleteAllBySceneId(sceneId);
            log.info("Deleted scene videos for sceneId: {}", sceneId);
            
            // Scene 삭제
            sceneRepository.delete(scene);
            
            log.info("Scene deleted successfully: sceneId={}, projectId={}", sceneId, projectId);
            
        } catch (Exception e) {
            log.error("Failed to delete scene: sceneId={}, projectId={}", sceneId, projectId, e);
            throw new BusinessException(ErrorCode.SCENE_DELETION_FAILED);
        }
    }
    
    @Override
    public SceneDesignResponse getSceneDesign(Long projectId, Long sceneId, String loginId) {
        log.info("Getting scene design: projectId={}, sceneId={}, loginId={}", projectId, sceneId, loginId);
        
        // 권한 체크
        Scene scene = sceneRepository.findByIdAndProjectId(sceneId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));
        
        if (!scene.getProject().getUser().getLoginId().equals(loginId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        
        // OptionalElements 파싱
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
        log.info("Regenerating scene design: projectId={}, sceneId={}, loginId={}", projectId, sceneId, loginId);
        
        // 권한 체크
        Scene scene = sceneRepository.findByIdAndProjectId(sceneId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));
        
        if (!scene.getProject().getUser().getLoginId().equals(loginId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        
        try {
            // OpenAI로 새로운 설계 생성 (기존 summary 유지, variation 생성)
            String variationRequest = request != null ? request.getUserRequest() : "같은 장면의 다른 연출 버전을 만들어주세요. 카메라 구도, 시간대, 무드, 조명을 다르게 구성해주세요.";
            
            log.info("OpenAI 호출 - sceneSummary: {}, variationRequest: {}", 
                    scene.getSummary(), variationRequest);
            
            String aiResponse = openAIService.designScene(
                    scene.getSummary(),
                    variationRequest
            );
            log.info("OpenAI 씬 설계 재추천 응답: {}", aiResponse);
            
            // JSON 추출 및 파싱
            String json = JsonUtils.extractJsonSafely(aiResponse);
            log.info("추출된 JSON: {}", json);
            OptionalElements newOptionalElements = parseOptionalElements(json);
            
            // 새로운 프롬프트 생성
            String newImagePrompt = generateImagePromptFromElements(scene.getSummary(), newOptionalElements);
            String newVideoPrompt = generateVideoPromptFromElements(scene.getSummary(), newOptionalElements);
            
            // Scene 업데이트
            scene.setOptionalElements(createOptionalElementsJson(newOptionalElements));
            scene.setImagePrompt(newImagePrompt);
            scene.setVideoPrompt(newVideoPrompt);
            Scene updatedScene = sceneRepository.save(scene);
            
            return SceneDesignResponse.builder()
                    .sceneId(updatedScene.getId())
                    .summary(updatedScene.getSummary())
                    .optionalElements(newOptionalElements)
                    .imagePrompt(updatedScene.getImagePrompt())
                    .videoPrompt(updatedScene.getVideoPrompt())
                    .displayText("같은 장면을 다른 연출로 다시 추천했습니다.")
                    .updatedAt(updatedScene.getUpdatedAt())
                    .build();
            
        } catch (BusinessException e) {
            // OpenAI 실패 시 fallback 사용
            log.warn("OpenAI API 실패, fallback 규칙 기반 응답 사용: {}", e.getMessage());
            
            String variationRequest = request != null ? request.getUserRequest() : "같은 장면의 다른 연출 버전";
            String fallbackResponse = openAIService.generateFallbackResponse(scene.getSummary(), variationRequest);
            
            // fallback 응답 파싱
            String json = JsonUtils.extractJsonSafely(fallbackResponse);
            log.info("Fallback에서 추출된 JSON: {}", json);
            OptionalElements newOptionalElements = parseOptionalElements(json);
            
            // 새로운 프롬프트 생성
            String newImagePrompt = generateImagePromptFromElements(scene.getSummary(), newOptionalElements);
            String newVideoPrompt = generateVideoPromptFromElements(scene.getSummary(), newOptionalElements);
            
            // Scene 업데이트
            scene.setOptionalElements(createOptionalElementsJson(newOptionalElements));
            scene.setImagePrompt(newImagePrompt);
            scene.setVideoPrompt(newVideoPrompt);
            Scene updatedScene = sceneRepository.save(scene);
            
            return SceneDesignResponse.builder()
                    .sceneId(updatedScene.getId())
                    .summary(updatedScene.getSummary())
                    .optionalElements(newOptionalElements)
                    .imagePrompt(updatedScene.getImagePrompt())
                    .videoPrompt(updatedScene.getVideoPrompt())
                    .displayText("같은 장면을 다른 연출로 다시 추천했습니다. (Fallback)")
                    .updatedAt(updatedScene.getUpdatedAt())
                    .build();
        }
    }
    
    /**
     * Scene 엔티티를 SceneResponse로 변환
     */
    private SceneResponse convertToResponse(Scene scene) {
        // Service 레이어에서 JSON 파싱 수행
        OptionalElements optionalElementsObj = parseOptionalElements(scene.getOptionalElements());
        // 대표 URL 계산
        String imageUrl = getRepresentativeImageUrl(scene.getId());
        String videoUrl = getRepresentativeVideoUrl(scene.getId());
        return SceneResponse.from(scene, optionalElementsObj, imageUrl, videoUrl);
    }
    
    /**
     * OptionalElements로부터 이미지 프롬프트 생성
     */
    private String generateImagePromptFromElements(String summary, OptionalElements elements) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(summary);
        
        if (elements != null) {
            if (elements.getAction() != null && !elements.getAction().isEmpty()) {
                prompt.append(", ").append(elements.getAction());
            }
            if (elements.getPose() != null && !elements.getPose().isEmpty()) {
                prompt.append(", ").append(elements.getPose());
            }
            if (elements.getCamera() != null && !elements.getCamera().isEmpty()) {
                prompt.append(", ").append(elements.getCamera());
            }
            if (elements.getLighting() != null && !elements.getLighting().isEmpty()) {
                prompt.append(", ").append(elements.getLighting());
            }
            if (elements.getMood() != null && !elements.getMood().isEmpty()) {
                prompt.append(", ").append(elements.getMood());
            }
            if (elements.getTimeOfDay() != null && !elements.getTimeOfDay().isEmpty()) {
                prompt.append(", ").append(elements.getTimeOfDay());
            }
        }
        
        prompt.append(", cinematic, high quality, detailed");
        return prompt.toString();
    }
    
    /**
     * OptionalElements로부터 영상 프롬프트 생성
     */
    private String generateVideoPromptFromElements(String summary, OptionalElements elements) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Video: ").append(summary);
        
        if (elements != null) {
            if (elements.getAction() != null && !elements.getAction().isEmpty()) {
                prompt.append(", ").append(elements.getAction());
            }
            if (elements.getCamera() != null && !elements.getCamera().isEmpty()) {
                prompt.append(", ").append(elements.getCamera());
            }
            if (elements.getLighting() != null && !elements.getLighting().isEmpty()) {
                prompt.append(", ").append(elements.getLighting());
            }
            if (elements.getMood() != null && !elements.getMood().isEmpty()) {
                prompt.append(", ").append(elements.getMood());
            }
            if (elements.getTimeOfDay() != null && !elements.getTimeOfDay().isEmpty()) {
                prompt.append(", ").append(elements.getTimeOfDay());
            }
        }
        
        prompt.append(", smooth motion, cinematic video");
        return prompt.toString();
    }
    
    /**
     * JSON 문자열을 OptionalElements 객체로 변환
     */
    private OptionalElements parseOptionalElements(String json) {
        try {
            if (json == null || json.trim().isEmpty()) {
                return OptionalElements.builder().build();
            }
            return objectMapper.readValue(json, OptionalElements.class);
        } catch (Exception e) {
            log.error("Failed to parse optionalElements JSON: {}", json, e);
            return OptionalElements.builder().build();
        }
    }
}
