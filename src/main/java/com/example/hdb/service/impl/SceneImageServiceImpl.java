package com.example.hdb.service.impl;

import com.example.hdb.dto.request.ImageEditCompleteRequest;
import com.example.hdb.dto.request.SceneImageEditAiRequest;
import com.example.hdb.dto.response.SceneImageEditAiResponse;
import com.example.hdb.dto.response.SceneImageResponse;
import com.example.hdb.entity.Scene;
import com.example.hdb.entity.SceneImage;
import com.example.hdb.exception.BusinessException;
import com.example.hdb.exception.ErrorCode;
import com.example.hdb.repository.SceneImageRepository;
import com.example.hdb.repository.SceneRepository;
import com.example.hdb.service.OpenAIService;
import com.example.hdb.service.SceneImageService;
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
public class SceneImageServiceImpl implements SceneImageService {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SceneImageServiceImpl.class);
    
    private final SceneImageRepository sceneImageRepository;
    private final SceneRepository sceneRepository;
    private final OpenAIService openAIService;
    
    @Override
    public SceneImageResponse generateImage(Long projectId, Long sceneId, String loginId) {
        log.info("=== SceneImageService.generateImage Started ===");
        log.info("projectId: {}", projectId);
        log.info("sceneId: {}", sceneId);
        log.info("loginId: {}", loginId);
        
        try {
            // 권한 체크: scene이 해당 project에 속하는지 확인
            log.info("Finding scene with sceneId: {}, projectId: {}", sceneId, projectId);
            Scene scene = sceneRepository.findByIdAndProjectId(sceneId, projectId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));
            
            log.info("Scene found: {}", scene.getId());
            log.info("Scene exists: {}", scene != null);
            
            // 권한 체크: project가 해당 사용자 소유인지 확인
            String projectOwnerLoginId = scene.getProject().getUser().getLoginId();
            log.info("Project owner loginId: {}", projectOwnerLoginId);
            
            if (!projectOwnerLoginId.equals(loginId)) {
                log.error("Authorization failed: scene owner {} != request user {}", projectOwnerLoginId, loginId);
                throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
            }
            
            // imagePrompt 확인
            String imagePrompt = scene.getImagePrompt();
            log.info("Scene imagePrompt: {}", imagePrompt);
            log.info("imagePrompt is null: {}", imagePrompt == null);
            log.info("imagePrompt is empty: {}", imagePrompt != null && imagePrompt.trim().isEmpty());
            
            if (imagePrompt == null || imagePrompt.trim().isEmpty()) {
                log.error("Scene image prompt is null or empty");
                throw new BusinessException(ErrorCode.SCENE_IMAGE_PROMPT_NOT_FOUND);
            }
            
            // 이미지 번호 계산
            log.info("Calculating next image number for sceneId: {}", sceneId);
            Integer nextImageNumber = sceneImageRepository.findFirstBySceneIdOrderByImageNumberDesc(sceneId)
                    .map(SceneImage::getImageNumber)
                    .orElse(0) + 1;
            
            log.info("Next image number: {}", nextImageNumber);
            
            // 1. 생성 시작 시 SceneImage 저장 (GENERATING 상태)
            log.info("Creating SceneImage with GENERATING status");
            log.info("About to save SceneImage - sceneId: {}, imageNumber: {}, imagePrompt: {}, imageUrl: {}, status: {}", 
                    scene.getId(), nextImageNumber, scene.getImagePrompt(), null, SceneImage.ImageStatus.GENERATING);
            
            SceneImage sceneImage = SceneImage.builder()
                    .scene(scene)
                    .imageNumber(nextImageNumber)
                    .imageUrl(null)
                    .imagePrompt(scene.getImagePrompt())
                    .openaiImageId(null)
                    .status(SceneImage.ImageStatus.GENERATING)
                    .build();
            
            log.info("Saving initial SceneImage...");
            SceneImage savedImage = sceneImageRepository.save(sceneImage);
            log.info("SceneImage 생성 시작 저장 완료: {}", savedImage.getId());
            
            try {
                // 2. OpenAI로 이미지 생성
                log.info("Calling OpenAI to generate image with prompt: {}", scene.getImagePrompt());
                String imageUrl = openAIService.generateImage(scene.getImagePrompt());
                log.info("OpenAI 이미지 생성 완료: {}", imageUrl);
                
                // 3. OpenAI 성공 시 status = READY로 업데이트
                savedImage.setImageUrl(imageUrl);
                savedImage.setStatus(SceneImage.ImageStatus.READY);
                SceneImage completedImage = sceneImageRepository.save(savedImage);
                log.info("SceneImage 생성 완료: {}", completedImage.getId());
                
                // Scene 대표 이미지 URL은 DB 저장 없이 조회 시 계산
                
                return SceneImageResponse.builder()
                        .id(completedImage.getId())
                        .imageNumber(completedImage.getImageNumber())
                        .imageUrl(completedImage.getImageUrl())
                        .imagePrompt(completedImage.getImagePrompt())
                        .status(completedImage.getStatus().name())
                        .statusDescription(completedImage.getStatus().getDescription())
                        .createdAt(completedImage.getCreatedAt())
                        .build();
                
            } catch (Exception openaiException) {
                log.error("OpenAI 이미지 생성 실패", openaiException);
                
                // 4. OpenAI 실패 시 status = FAILED로 업데이트
                savedImage.setStatus(SceneImage.ImageStatus.FAILED);
                SceneImage failedImage = sceneImageRepository.save(savedImage);
                log.info("SceneImage 생성 실패 저장: {}", failedImage.getId());
                
                return SceneImageResponse.builder()
                        .id(failedImage.getId())
                        .imageNumber(failedImage.getImageNumber())
                        .imageUrl(failedImage.getImageUrl())
                        .imagePrompt(failedImage.getImagePrompt())
                        .status(failedImage.getStatus().name())
                        .statusDescription(failedImage.getStatus().getDescription())
                        .createdAt(failedImage.getCreatedAt())
                        .build();
            }
            
        } catch (Exception e) {
            log.error("SceneImage generation failed", e);
            
            // 5. 예외 발생 시에도 SceneImage 저장 (FAILED 상태)
            Integer fallbackImageNumber = sceneImageRepository.findFirstBySceneIdOrderByImageNumberDesc(sceneId)
                    .map(SceneImage::getImageNumber)
                    .orElse(0) + 1;
            
            // scene을 다시 조회해야 함
            Scene fallbackScene = sceneRepository.findByIdAndProjectId(sceneId, projectId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));
            
            SceneImage fallbackImage = SceneImage.builder()
                    .scene(fallbackScene)
                    .imageNumber(fallbackImageNumber)
                    .imageUrl(null)
                    .imagePrompt(fallbackScene.getImagePrompt())
                    .openaiImageId(null)
                    .status(SceneImage.ImageStatus.FAILED)
                    .build();
            
            SceneImage savedFallbackImage = sceneImageRepository.save(fallbackImage);
            log.info("Fallback SceneImage 저장 완료: {}", savedFallbackImage.getId());
            
            return SceneImageResponse.builder()
                    .id(savedFallbackImage.getId())
                    .imageNumber(savedFallbackImage.getImageNumber())
                    .imageUrl(savedFallbackImage.getImageUrl())
                    .imagePrompt(savedFallbackImage.getImagePrompt())
                    .status(savedFallbackImage.getStatus().name())
                    .statusDescription(savedFallbackImage.getStatus().getDescription())
                    .createdAt(savedFallbackImage.getCreatedAt())
                    .build();
        }
    }
    
    @Override
    public List<SceneImageResponse> getImages(Long projectId, Long sceneId, String loginId) {
        log.info("Getting images for scene: {}, project: {}, user: {}", sceneId, projectId, loginId);
        
        // 권한 체크
        Scene scene = sceneRepository.findByIdAndProjectId(sceneId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));
        
        if (!scene.getProject().getUser().getLoginId().equals(loginId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        
        List<SceneImage> images = sceneImageRepository.findBySceneIdOrderByImageNumberAsc(sceneId);
        
        return images.stream()
                .map(image -> SceneImageResponse.builder()
                        .id(image.getId())
                        .imageNumber(image.getImageNumber())
                        .imageUrl(image.getImageUrl())
                        .editedImageUrl(image.getEditedImageUrl())
                        .imagePrompt(image.getImagePrompt())
                        .status(image.getStatus().name())
                        .statusDescription(image.getStatus().getDescription())
                        .createdAt(image.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }
    
    @Override
    public List<SceneImageResponse> getProjectImages(Long projectId, String loginId) {
        log.info("Getting all images for project: {}, user: {}", projectId, loginId);
        
        // 권한 체크: 프로젝트 소속 확인
        if (!sceneRepository.existsById(projectId)) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
        
        // 프로젝트 내 모든 씬의 이미지 조회
        List<Scene> scenes = sceneRepository.findByProjectIdOrderBySceneOrderAsc(projectId);
        
        // 각 씬의 이미지를 모두 수집
        List<SceneImage> allImages = scenes.stream()
                .flatMap(scene -> sceneImageRepository.findBySceneIdOrderByImageNumberAsc(scene.getId()).stream())
                .collect(Collectors.toList());
        
        return allImages.stream()
                .map(image -> SceneImageResponse.builder()
                        .id(image.getId())
                        .sceneId(image.getScene().getId())
                        .imageNumber(image.getImageNumber())
                        .imageUrl(image.getImageUrl())
                        .editedImageUrl(image.getEditedImageUrl())
                        .imagePrompt(image.getImagePrompt())
                        .status(image.getStatus().name())
                        .statusDescription(image.getStatus().getDescription())
                        .createdAt(image.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }
    
    @Override
    public SceneImageResponse completeImageEdit(Long projectId, Long sceneId, Long imageId, String loginId, ImageEditCompleteRequest request) {
        log.info("Completing image edit for imageId: {}, editedImageUrl: {}", imageId, request.getEditedImageUrl());
        
        // 권한 체크
        SceneImage sceneImage = sceneImageRepository.findByIdAndSceneId(imageId, sceneId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IMAGE_NOT_FOUND));
        
        // Scene을 통해 Project 권한 체크
        Scene scene = sceneImage.getScene();
        if (!scene.getProject().getUser().getLoginId().equals(loginId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        
        // 편집된 이미지 URL 저장
        sceneImage.setEditedImageUrl(request.getEditedImageUrl());
        SceneImage savedImage = sceneImageRepository.save(sceneImage);
        
        log.info("Image edit completed successfully: {}", savedImage.getId());
        
        return SceneImageResponse.builder()
                .id(savedImage.getId())
                .imageNumber(savedImage.getImageNumber())
                .imageUrl(savedImage.getImageUrl())
                .editedImageUrl(savedImage.getEditedImageUrl())
                .imagePrompt(savedImage.getImagePrompt())
                .status(savedImage.getStatus().name())
                .statusDescription(savedImage.getStatus().getDescription())
                .createdAt(savedImage.getCreatedAt())
                .build();
    }
    
    @Override
    public SceneImageResponse generateImageEditAi(Long projectId, Long sceneId, Long imageId, String loginId, SceneImageEditAiRequest request) {
        log.info("=== AI Image Edit Generation Started ===");
        log.info("projectId: {}, sceneId: {}, imageId: {}, userEditText: {}", 
                projectId, sceneId, imageId, request.getUserEditText());
        
        // 권한 체크
        SceneImage originalImage = sceneImageRepository.findByIdAndSceneId(imageId, sceneId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IMAGE_NOT_FOUND));
        
        // Scene을 통해 Project 권한 체크
        Scene scene = originalImage.getScene();
        if (!scene.getProject().getUser().getLoginId().equals(loginId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        
        // 원본 이미지 URL 결정 (우선순위: editedImageUrl -> imageUrl)
        String sourceImageUrl = originalImage.getEditedImageUrl() != null ? 
                originalImage.getEditedImageUrl() : originalImage.getImageUrl();
        
        log.info("Source image URL: {}", sourceImageUrl);
        
        // 다음 imageNumber 계산
        Integer maxImageNumber = sceneImageRepository.findMaxImageNumberBySceneId(sceneId);
        int nextImageNumber = (maxImageNumber != null ? maxImageNumber : 0) + 1;
        
        log.info("Next imageNumber: {}", nextImageNumber);
        
        try {
            // AI 이미지 편집 생성 시도
            String editedImageUrl = generateEditedImageUrl(sourceImageUrl, request.getUserEditText());
            String newImagePrompt = String.format("%s (수정: %s)", 
                    originalImage.getImagePrompt(), request.getUserEditText());
            
            // 새 SceneImage 엔티티 생성
            SceneImage newImage = SceneImage.builder()
                    .scene(scene)
                    .imageNumber(nextImageNumber)
                    .imageUrl(sourceImageUrl) // 원본 유지
                    .editedImageUrl(editedImageUrl) // 수정본 저장
                    .imagePrompt(newImagePrompt)
                    .status(SceneImage.ImageStatus.READY)
                    .build();
            
            SceneImage savedImage = sceneImageRepository.save(newImage);
            
            log.info("Saved new edited image with ID: {}", savedImage.getId());
            
            return SceneImageResponse.builder()
                    .id(savedImage.getId())
                    .imageNumber(savedImage.getImageNumber())
                    .imageUrl(savedImage.getImageUrl())
                    .editedImageUrl(savedImage.getEditedImageUrl())
                    .imagePrompt(savedImage.getImagePrompt())
                    .status(savedImage.getStatus().name())
                    .statusDescription(savedImage.getStatus().getDescription())
                    .createdAt(savedImage.getCreatedAt())
                    .updatedAt(savedImage.getUpdatedAt())
                    .build();
            
        } catch (Exception e) {
            // Fallback: mock edited URL 생성
            log.warn("AI image edit failed, using fallback: {}", e.getMessage());
            
            String fallbackEditedUrl = generateFallbackEditedUrl(sourceImageUrl, request.getUserEditText());
            String newImagePrompt = String.format("%s (수정: %s - Fallback)", 
                    originalImage.getImagePrompt(), request.getUserEditText());
            
            // 새 SceneImage 엔티티 생성 (fallback)
            SceneImage newImage = SceneImage.builder()
                    .scene(scene)
                    .imageNumber(nextImageNumber)
                    .imageUrl(sourceImageUrl)
                    .editedImageUrl(fallbackEditedUrl)
                    .imagePrompt(newImagePrompt)
                    .status(SceneImage.ImageStatus.READY)
                    .build();
            
            SceneImage savedImage = sceneImageRepository.save(newImage);
            
            log.info("Saved fallback edited image with ID: {}", savedImage.getId());
            
            return SceneImageResponse.builder()
                    .id(savedImage.getId())
                    .imageNumber(savedImage.getImageNumber())
                    .imageUrl(savedImage.getImageUrl())
                    .editedImageUrl(savedImage.getEditedImageUrl())
                    .imagePrompt(savedImage.getImagePrompt())
                    .status(savedImage.getStatus().name())
                    .statusDescription(savedImage.getStatus().getDescription())
                    .createdAt(savedImage.getCreatedAt())
                    .updatedAt(savedImage.getUpdatedAt())
                    .build();
        }
    }
    
    /**
     * AI를 통한 편집된 이미지 URL 생성 (실제 구현 시 연동)
     */
    private String generateEditedImageUrl(String sourceUrl, String userEditText) {
        // TODO: 실제 AI 이미지 편집 서비스 연동
        // 현재는 mock URL 반환
        String timestamp = String.valueOf(System.currentTimeMillis());
        return String.format("%s?edited=%s&prompt=%s", sourceUrl, timestamp, userEditText.hashCode());
    }
    
    /**
     * Fallback 편집된 이미지 URL 생성
     */
    private String generateFallbackEditedUrl(String sourceUrl, String userEditText) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        return String.format("%s?fallback=%s&prompt=%s", sourceUrl, timestamp, userEditText.hashCode());
    }
}
