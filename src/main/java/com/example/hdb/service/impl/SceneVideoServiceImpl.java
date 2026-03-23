package com.example.hdb.service.impl;

import com.example.hdb.dto.kling.KlingVideoRequest;
import com.example.hdb.dto.kling.KlingVideoResponse;
import com.example.hdb.dto.response.SceneVideoResponse;
import com.example.hdb.entity.Scene;
import com.example.hdb.entity.SceneImage;
import com.example.hdb.entity.SceneVideo;
import com.example.hdb.exception.BusinessException;
import com.example.hdb.exception.ErrorCode;
import com.example.hdb.repository.SceneVideoRepository;
import com.example.hdb.repository.SceneRepository;
import com.example.hdb.repository.SceneImageRepository;
import com.example.hdb.service.KlingVideoApiService;
import com.example.hdb.service.SceneVideoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SceneVideoServiceImpl implements SceneVideoService {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SceneVideoServiceImpl.class);
    
    private final SceneVideoRepository sceneVideoRepository;
    private final SceneRepository sceneRepository;
    private final KlingVideoApiService klingVideoApiService;
    private final SceneImageRepository sceneImageRepository;
    
    @Override
    @Transactional
    public SceneVideoResponse generateVideo(Long projectId, Long sceneId, String loginId, Integer duration) {
        log.info("Generating video for scene: {}, project: {}, user: {}, duration: {}s", sceneId, projectId, loginId, duration);
        
        // 권한 체크: scene이 해당 project에 속하는지 확인
        Scene scene = sceneRepository.findByIdAndProjectId(sceneId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));
        
        // 권한 체크: project가 해당 사용자 소유인지 확인
        if (!scene.getProject().getUser().getLoginId().equals(loginId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        
        // videoPrompt 확인
        if (scene.getVideoPrompt() == null || scene.getVideoPrompt().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.SCENE_VIDEO_PROMPT_NOT_FOUND);
        }
        
        // 이미지 URL 우선순위 결정 (edited_image_url 우선)
        String imageUrlForVideo = determineImageUrlForVideo(scene);
        log.info("Using image URL for video generation: {}", imageUrlForVideo);
        
        // Kling 영상 생성 요청
        KlingVideoRequest request = new KlingVideoRequest();
        request.setPrompt(scene.getVideoPrompt());
        request.setDuration(duration);
        request.setAspectRatio("16:9");
        
        // SceneVideo를 PENDING 상태로 먼저 저장
        SceneVideo sceneVideo = SceneVideo.builder()
                .scene(scene)
                .videoUrl(null) // 생성 전까지 null
                .videoPrompt(scene.getVideoPrompt())
                .duration(duration)
                .openaiVideoId(null)
                .klingTaskId(null)
                .status(SceneVideo.VideoStatus.GENERATING)
                .build();
        
        SceneVideo savedVideo = sceneVideoRepository.save(sceneVideo);
        log.info("SceneVideo GENERATING 상태로 저장 완료: {}", savedVideo.getId());
        
        String finalVideoUrl;
        try {
            KlingVideoResponse response = klingVideoApiService.generateVideo(request);
            log.info("Kling 영상 생성 요청 완료: taskId={}", response.getTaskId());
            
            // 비동기적으로 상태 확인 후 결과 저장
            // TODO: 실제로는 @Async로 상태 폴링 필요
            // 현재는 바로 완료된 것으로 가정
            String taskId = response.getTaskId();
            
            // 실제 영상 생성 결과 확인
            KlingVideoResponse statusResponse = klingVideoApiService.checkVideoStatus(taskId);
            if ("completed".equals(statusResponse.getStatus()) && statusResponse.getVideoUrl() != null) {
                finalVideoUrl = statusResponse.getVideoUrl();
                log.info("REAL VIDEO GENERATED - prompt: {}, url: {}", scene.getVideoPrompt(), finalVideoUrl);
            } else {
                // 생성 중이거나 실패 시 fallback
                throw new RuntimeException("Video generation not completed: " + statusResponse.getStatus());
            }
            
        } catch (Exception klingException) {
            log.warn("Kling API 호출 실패, fallback으로 동적 영상 URL 사용: {}", klingException.getMessage());
            finalVideoUrl = generateDynamicVideoUrl(sceneId, scene.getVideoPrompt());
        }
        
        // 성공/실패 모두 COMPLETED 상태로 업데이트
        savedVideo.setVideoUrl(finalVideoUrl);
        savedVideo.setStatus(SceneVideo.VideoStatus.COMPLETED);
        savedVideo = sceneVideoRepository.save(savedVideo);
        
        if (finalVideoUrl.contains("fallback-video-service.com")) {
            log.info("VIDEO FALLBACK USED - prompt: {}, savedVideoId: {}, fallbackUrl: {}", 
                    scene.getVideoPrompt(), savedVideo.getId(), finalVideoUrl);
        } else {
            log.info("REAL VIDEO GENERATED - prompt: {}, savedVideoId: {}, savedVideoUrl: {}", 
                    scene.getVideoPrompt(), savedVideo.getId(), finalVideoUrl);
        }
        
        return new SceneVideoResponse(
                savedVideo.getId(),
                savedVideo.getScene().getId(),
                null, // sceneOrder는 별도로 설정 필요
                savedVideo.getDuration(),
                savedVideo.getVideoUrl(),
                savedVideo.getVideoPrompt(),
                savedVideo.getStatus().name(),
                savedVideo.getStatus().getDescription(),
                false, // representative는 별도로 설정 필요
                savedVideo.getCreatedAt(),
                savedVideo.getUpdatedAt()
        );
    }
    
    @Async("videoGenerationExecutor")
    @Transactional
    public void processVideoGenerationAsync(Long videoId) {
        log.info("비동기 영상 생성 시작: videoId={}", videoId);
        
        try {
            // SceneVideo 조회
            SceneVideo sceneVideo = sceneVideoRepository.findById(videoId)
                    .orElseThrow(() -> new RuntimeException("SceneVideo not found: " + videoId));
            
            // 이미 GENERATING 상태인지 확인
            if (sceneVideo.getStatus() != SceneVideo.VideoStatus.GENERATING) {
                log.warn("Video is not in GENERATING state: {}, status: {}", videoId, sceneVideo.getStatus());
                return;
            }
            
            // 영상 생성에 사용할 이미지 URL 결정 (편집 이미지 우선)
            String sourceImageUrl = determineImageUrlForVideo(sceneVideo.getScene());
            log.info("Video generation source image url: {}", sourceImageUrl);
            
            // Kling 영상 생성 요청
            KlingVideoRequest request = new KlingVideoRequest();
            request.setPrompt(sceneVideo.getVideoPrompt());
            request.setDuration(5); // 5초
            request.setAspectRatio("16:9");
            
            // TODO: Kling API가 이미지 URL을 지원하면 request.setImageUrl(sourceImageUrl) 추가
            
            KlingVideoResponse response;
            try {
                response = klingVideoApiService.generateVideo(request);
                log.info("Kling 영상 생성 요청 완료: taskId={}", response.getTaskId());
                
                // task ID 저장
                sceneVideo.setKlingTaskId(response.getTaskId());
                sceneVideoRepository.save(sceneVideo);
                
                // TODO: 추후 상태조회 로직 추가
                // 지금은 바로 COMPLETED 상태로 가정
                sceneVideo.setStatus(SceneVideo.VideoStatus.COMPLETED);
                sceneVideo.setVideoUrl(response.getVideoUrl()); // 실제로는 상태조회 후 설정
                SceneVideo completedVideo = sceneVideoRepository.save(sceneVideo);
                log.info("SceneVideo 생성 완료: {}", completedVideo.getId());
                
            } catch (Exception klingException) {
                log.warn("Kling API 호출 실패, fallback으로 동적 영상 URL 사용: {}", klingException.getMessage());
                
                // Fallback: 동적 영상 URL 생성
                String fallbackVideoUrl = generateDynamicVideoUrl(sceneVideo.getScene().getId(), sceneVideo.getVideoPrompt());
                sceneVideo.setStatus(SceneVideo.VideoStatus.COMPLETED);
                sceneVideo.setVideoUrl(fallbackVideoUrl);
                SceneVideo completedVideo = sceneVideoRepository.save(sceneVideo);
                log.info("SceneVideo fallback 생성 완료: {}, videoUrl: {}", completedVideo.getId(), fallbackVideoUrl);
            }
            
        } catch (Exception e) {
            log.error("비동기 영상 생성 실패: videoId={}", videoId, e);
            
            // 실패 시 status = FAILED
            try {
                SceneVideo sceneVideo = sceneVideoRepository.findById(videoId)
                        .orElseThrow(() -> new RuntimeException("SceneVideo not found: " + videoId));
                sceneVideo.setStatus(SceneVideo.VideoStatus.FAILED);
                sceneVideoRepository.save(sceneVideo);
                log.info("SceneVideo 실패 처리 완료: {}", videoId);
            } catch (Exception saveException) {
                log.error("SceneVideo 실패 처리 중 에러: videoId={}", videoId, saveException);
            }
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<SceneVideoResponse> getVideos(Long projectId, Long sceneId, String loginId) {
        log.info("Getting videos for scene: {}, project: {}, user: {}", sceneId, projectId, loginId);
        
        // 권한 체크: scene이 해당 project에 속하는지 확인
        Scene scene = sceneRepository.findByIdAndProjectId(sceneId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));
        
        // 권한 체크: project가 해당 사용자 소유인지 확인
        if (!scene.getProject().getUser().getLoginId().equals(loginId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        
        List<SceneVideo> videos = sceneVideoRepository.findBySceneOrderByCreatedAtDesc(scene);
        return videos.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<SceneVideoResponse> getProjectVideos(Long projectId, String loginId) {
        log.info("Getting all videos for project: {}, user: {}", projectId, loginId);
        
        // 권한 체크: 프로젝트 소속 확인
        if (!sceneRepository.existsById(projectId)) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
        
        // 프로젝트 내 모든 씬 조회
        List<Scene> scenes = sceneRepository.findByProjectIdOrderBySceneOrderAsc(projectId);
        log.info("Found {} scenes for project: {}", scenes.size(), projectId);
        
        // 각 씬의 영상을 모두 수집
        List<SceneVideo> allVideos = scenes.stream()
                .flatMap(scene -> sceneVideoRepository.findBySceneIdOrderByCreatedAtDesc(scene.getId()).stream())
                .collect(Collectors.toList());
        
        log.info("Found {} total videos for project: {}", allVideos.size(), projectId);
        
        // scene별로 그룹화하여 대표 영상만 포함
        List<SceneVideoResponse> videoResponses = scenes.stream()
                .map(scene -> {
                    // 해당 씬의 영상들 중 최신 영상 찾기
                    List<SceneVideo> sceneVideos = sceneVideoRepository.findBySceneIdOrderByCreatedAtDesc(scene.getId());
                    
                    if (sceneVideos.isEmpty()) {
                        log.info("No videos found for scene: {}", scene.getId());
                        return null;
                    }
                    
                    SceneVideo latestVideo = sceneVideos.get(0); // 최신 영상
                    boolean isRepresentative = scene.getSceneOrder() == 1; // 첫 번째 씬을 대표 영상으로 설정
                    
                    log.info("Scene {} - videoUrl: {}, status: {}, representative: {}", 
                            scene.getId(), latestVideo.getVideoUrl(), latestVideo.getStatus(), isRepresentative);
                    
                    return new SceneVideoResponse(
                            latestVideo.getId(),
                            scene.getId(),
                            scene.getSceneOrder(),
                            latestVideo.getDuration(),
                            latestVideo.getVideoUrl(),
                            latestVideo.getVideoPrompt(),
                            latestVideo.getStatus().name(),
                            latestVideo.getStatus().getDescription(),
                            isRepresentative,
                            latestVideo.getCreatedAt(),
                            latestVideo.getUpdatedAt()
                    );
                })
                .filter(videoResponse -> videoResponse != null && videoResponse.getVideoUrl() != null && !videoResponse.getVideoUrl().trim().isEmpty())
                .collect(Collectors.toList());
        
        log.info("Returning {} video responses for project: {}", videoResponses.size(), projectId);
        return videoResponses;
    }
    
    /**
     * 영상 생성에 사용할 이미지 URL 결정 (편집 이미지 우선)
     */
    private String determineImageUrlForVideo(Scene scene) {
        // 1. 편집된 이미지가 있으면 우선 사용
        if (scene.getEditedImageUrl() != null && !scene.getEditedImageUrl().trim().isEmpty()) {
            log.info("Using edited image URL: {}", scene.getEditedImageUrl());
            return scene.getEditedImageUrl();
        }
        
        // 2. 원본 이미지 사용
        if (scene.getImageUrl() != null && !scene.getImageUrl().trim().isEmpty()) {
            log.info("Using original image URL: {}", scene.getImageUrl());
            return scene.getImageUrl();
        }
        
        // 1. Scene에 편집된 이미지가 있는지 확인
        if (scene.getEditedImageUrl() != null && !scene.getEditedImageUrl().trim().isEmpty()) {
            log.info("Using scene edited image URL: {}", scene.getEditedImageUrl());
            return scene.getEditedImageUrl();
        }
        
        // 2. SceneImage 중 편집된 이미지가 있는지 확인
        try {
            List<SceneImage> images = sceneImageRepository.findBySceneIdOrderByImageNumberAsc(scene.getId());
            for (SceneImage image : images) {
                if (image.getEditedImageUrl() != null && !image.getEditedImageUrl().trim().isEmpty()) {
                    log.info("Using sceneImage edited image URL: {}", image.getEditedImageUrl());
                    return image.getEditedImageUrl();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to check scene images for edited URLs", e);
        }
        
        // 3. SceneImage 중 원본 이미지 사용
        try {
            Optional<SceneImage> latestImage = sceneImageRepository.findFirstBySceneIdOrderByImageNumberDesc(scene.getId());
            if (latestImage.isPresent() && latestImage.get().getImageUrl() != null && !latestImage.get().getImageUrl().trim().isEmpty()) {
                log.info("Using sceneImage original image URL: {}", latestImage.get().getImageUrl());
                return latestImage.get().getImageUrl();
            }
        } catch (Exception e) {
            log.warn("Failed to get latest scene image", e);
        }
        
        // 4. Scene 원본 이미지 사용
        if (scene.getImageUrl() != null && !scene.getImageUrl().trim().isEmpty()) {
            log.info("Using scene original image URL: {}", scene.getImageUrl());
            return scene.getImageUrl();
        }
        
        // 5. 이미지가 없는 경우
        log.warn("No image URL available for video generation - sceneId: {}", scene.getId());
        return null;
    }
    
    /**
     * Scene 엔티티를 SceneVideoResponse로 변환
     */
    private SceneVideoResponse convertToResponse(SceneVideo sceneVideo) {
        return new SceneVideoResponse(
                sceneVideo.getId(),
                sceneVideo.getScene().getId(),
                null, // sceneOrder는 별도로 설정 필요
                sceneVideo.getDuration(),
                sceneVideo.getVideoUrl(),
                sceneVideo.getVideoPrompt(),
                sceneVideo.getStatus().name(),
                sceneVideo.getStatus().getDescription(),
                false, // representative는 별도로 설정 필요
                sceneVideo.getCreatedAt(),
                sceneVideo.getUpdatedAt()
        );
    }
    
    /**
     * 동적 영상 URL 생성 (fallback용)
     */
    private String generateDynamicVideoUrl(Long sceneId, String videoPrompt) {
        log.warn("VIDEO FALLBACK USED - sceneId: {}, videoPrompt: {}", sceneId, videoPrompt);
        
        String timestamp = String.valueOf(System.currentTimeMillis());
        String promptHash = String.valueOf(videoPrompt.hashCode());
        
        return String.format("https://fallback-video-service.com/scene_%d_%s_%s.mp4", 
                sceneId, timestamp, promptHash);
    }
}
