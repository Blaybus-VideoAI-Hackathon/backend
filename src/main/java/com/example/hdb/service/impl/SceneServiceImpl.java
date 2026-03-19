package com.example.hdb.service.impl;

import com.example.hdb.dto.request.SceneCreateRequest;
import com.example.hdb.dto.request.SceneDesignRequest;
import com.example.hdb.dto.request.SceneEditRequest;
import com.example.hdb.dto.request.SceneGenerateRequest;
import com.example.hdb.dto.request.SceneUpdateRequest;
import com.example.hdb.dto.response.SceneResponse;
import com.example.hdb.dto.common.OptionalElements;
import com.example.hdb.dto.openai.SceneGenerationResponse;
import com.example.hdb.dto.openai.SceneDesignResponse;
import com.example.hdb.entity.Project;
import com.example.hdb.entity.Scene;
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
@Slf4j
@Transactional
public class SceneServiceImpl implements SceneService {
    
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
            log.error("씬 생성 실패 - fallback 실행", e);
            
            // fallback: 기본 scene 2개 생성
            List<Scene> fallbackScenes = List.of(
                Scene.builder()
                        .project(project)
                        .sceneOrder(1)
                        .summary("강아지가 밥그릇을 발견한다")
                        .optionalElements(createOptionalElementsJson("발견", "호기심"))
                        .imagePrompt("A cute dog finds a food bowl with excitement")
                        .videoPrompt("A cute dog finds a food bowl and reacts with excitement")
                        .status(com.example.hdb.enums.SceneStatus.PENDING)
                        .build(),
                Scene.builder()
                        .project(project)
                        .sceneOrder(2)
                        .summary("강아지가 맛있게 밥을 먹는다")
                        .optionalElements(createOptionalElementsJson("먹기", "행복"))
                        .imagePrompt("A cute dog eating happily from its food bowl")
                        .videoPrompt("A cute dog eating happily with wagging tail")
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
            log.info("씬 설계 파싱 완료");
            
            // 씬 업데이트
            scene.setOptionalElements(createOptionalElementsJson(designResponse.getOptionalElements()));
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
            log.info("씬 수정 파싱 완료");
            
            // 씬 업데이트
            scene.setOptionalElements(createOptionalElementsJson(editResponse.getOptionalElements()));
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
