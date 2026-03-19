package com.example.hdb.service.impl;

import com.example.hdb.dto.kling.KlingVideoRequest;
import com.example.hdb.dto.kling.KlingVideoResponse;
import com.example.hdb.dto.response.SceneVideoResponse;
import com.example.hdb.entity.Scene;
import com.example.hdb.entity.SceneVideo;
import com.example.hdb.exception.BusinessException;
import com.example.hdb.exception.ErrorCode;
import com.example.hdb.repository.SceneVideoRepository;
import com.example.hdb.repository.SceneRepository;
import com.example.hdb.service.KlingVideoApiService;
import com.example.hdb.service.SceneVideoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SceneVideoServiceImpl implements SceneVideoService {
    
    private final SceneVideoRepository sceneVideoRepository;
    private final SceneRepository sceneRepository;
    private final KlingVideoApiService klingVideoApiService;
    
    @Override
    @Transactional
    public SceneVideoResponse generateVideo(Long projectId, Long sceneId, String loginId) {
        log.info("Generating video for scene: {}, project: {}, user: {}", sceneId, projectId, loginId);
        
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
        
        // 1. SceneVideo를 GENERATING 상태로 저장 후 즉시 반환
        SceneVideo sceneVideo = SceneVideo.builder()
                .scene(scene)
                .videoUrl(null)
                .videoPrompt(scene.getVideoPrompt())
                .openaiVideoId(null)
                .klingTaskId(null)
                .status(SceneVideo.VideoStatus.GENERATING)
                .build();
        
        SceneVideo savedVideo = sceneVideoRepository.save(sceneVideo);
        log.info("SceneVideo 생성 시작 저장 완료: {}", savedVideo.getId());
        
        // 2. 비동기로 영상 생성 시작
        processVideoGenerationAsync(savedVideo.getId());
        
        return SceneVideoResponse.builder()
                .id(savedVideo.getId())
                .videoUrl(savedVideo.getVideoUrl())
                .videoPrompt(savedVideo.getVideoPrompt())
                .status(savedVideo.getStatus().name())
                .statusDescription(savedVideo.getStatus().getDescription())
                .createdAt(savedVideo.getCreatedAt())
                .updatedAt(savedVideo.getUpdatedAt())
                .build();
    }
    
    @Async("videoGenerationExecutor")
    @Transactional
    public void processVideoGenerationAsync(Long videoId) {
        log.info("비동기 영상 생성 시작: videoId={}", videoId);
        
        try {
            // SceneVideo 조회
            SceneVideo sceneVideo = sceneVideoRepository.findById(videoId)
                    .orElseThrow(() -> new RuntimeException("SceneVideo not found: " + videoId));
            
            // Kling 영상 생성 요청
            KlingVideoRequest request = new KlingVideoRequest();
            request.setPrompt(sceneVideo.getVideoPrompt());
            request.setDuration(5); // 5초
            request.setAspectRatio("16:9");
            
            KlingVideoResponse response = klingVideoApiService.generateVideo(request);
            log.info("Kling 영상 생성 요청 완료: taskId={}", response.getTaskId());
            
            // task ID 저장
            sceneVideo.setKlingTaskId(response.getTaskId());
            sceneVideoRepository.save(sceneVideo);
            
            // TODO: 추후 상태조회 로직 추가
            // 지금은 바로 READY 상태로 가정
            sceneVideo.setStatus(SceneVideo.VideoStatus.READY);
            sceneVideo.setVideoUrl(response.getVideoUrl()); // 실제로는 상태조회 후 설정
            SceneVideo completedVideo = sceneVideoRepository.save(sceneVideo);
            log.info("SceneVideo 생성 완료: {}", completedVideo.getId());
            
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
    
    private SceneVideoResponse convertToResponse(SceneVideo sceneVideo) {
        return SceneVideoResponse.builder()
                .id(sceneVideo.getId())
                .videoUrl(sceneVideo.getVideoUrl())
                .videoPrompt(sceneVideo.getVideoPrompt())
                .status(sceneVideo.getStatus().name())
                .statusDescription(sceneVideo.getStatus().getDescription())
                .createdAt(sceneVideo.getCreatedAt())
                .updatedAt(sceneVideo.getUpdatedAt())
                .build();
    }
}
