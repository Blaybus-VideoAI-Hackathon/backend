package com.example.hdb.service.impl;

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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SceneImageServiceImpl implements SceneImageService {
    
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
                        .imagePrompt(image.getImagePrompt())
                        .status(image.getStatus().name())
                        .statusDescription(image.getStatus().getDescription())
                        .createdAt(image.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }
}
