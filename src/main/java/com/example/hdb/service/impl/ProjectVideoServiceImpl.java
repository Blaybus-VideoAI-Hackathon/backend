package com.example.hdb.service.impl;

import com.example.hdb.dto.request.VideoMergeRequest;
import com.example.hdb.dto.response.ProjectVideoResponse;
import com.example.hdb.entity.Project;
import com.example.hdb.entity.Scene;
import com.example.hdb.entity.SceneVideo;
import com.example.hdb.exception.BusinessException;
import com.example.hdb.exception.ErrorCode;
import com.example.hdb.repository.ProjectRepository;
import com.example.hdb.repository.SceneRepository;
import com.example.hdb.repository.SceneVideoRepository;
import com.example.hdb.service.ProjectVideoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ProjectVideoServiceImpl implements ProjectVideoService {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProjectVideoServiceImpl.class);
    
    private final ProjectRepository projectRepository;
    private final SceneRepository sceneRepository;
    private final SceneVideoRepository sceneVideoRepository;
    
    @Value("${app.video.output.path:/tmp/videos}")
    private String videoOutputPath;
    
    @Value("${app.video.base-url:http://localhost:8080/videos}")
    private String videoBaseUrl;
    
    @Override
    public ProjectVideoResponse mergeProjectVideos(Long projectId, String loginId, VideoMergeRequest request) {
        log.info("=== STARTING PROJECT VIDEO MERGE ===");
        log.info("projectId={}, loginId={}", projectId, loginId);
        
        // 권한 체크
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        
        if (!project.getUser().getLoginId().equals(loginId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        
        try {
            // 1. Scene 목록 조회 (scene_order 순서)
            List<Scene> scenes = sceneRepository.findByProjectIdOrderBySceneOrderAsc(projectId);
            
            if (scenes.isEmpty()) {
                throw new BusinessException(ErrorCode.SCENE_NOT_FOUND);
            }
            
            log.info("=== SCENE LIST FOR MERGE ===");
            for (Scene scene : scenes) {
                log.info("sceneId={}, sceneOrder={}, summary='{}'", 
                        scene.getId(), scene.getSceneOrder(), scene.getSummary());
            }
            
            // 2. 각 Scene의 COMPLETED 영상 수집
            List<String> videoUrls = new ArrayList<>();
            List<String> missingScenes = new ArrayList<>();
            
            for (Scene scene : scenes) {
                // 병합 직전 각 sceneId의 scene_videos raw 상태 로그
                log.info("=== CHECKING SCENE_VIDEOS RAW STATUS ===");
                log.info("sceneId={}, sceneOrder={}", scene.getId(), scene.getSceneOrder());
                
                List<SceneVideo> allSceneVideos = sceneVideoRepository.findBySceneIdOrderByCreatedAtDesc(scene.getId());
                for (SceneVideo sv : allSceneVideos) {
                    log.info("RAW VIDEO - videoId={}, status={}, videoUrl={}, createdAt={}", 
                            sv.getId(), sv.getStatus(), sv.getVideoUrl(), sv.getCreatedAt());
                }
                
                SceneVideo selectedVideo = getLatestMergeableSceneVideo(scene.getId());
                
                if (selectedVideo != null) {
                    videoUrls.add(selectedVideo.getVideoUrl());
                    
                    // 선택 이유 로그
                    String selectionReason;
                    if (selectedVideo.getStatus() == SceneVideo.VideoStatus.COMPLETED) {
                        selectionReason = "COMPLETED 우선 선택";
                    } else {
                        selectionReason = "GENERATING fallback 선택";
                    }
                    
                    log.info("SELECTED VIDEO FOR MERGE - sceneId={}, sceneOrder={}, videoId={}, status={}, videoUrl={}, reason={}", 
                            scene.getId(), scene.getSceneOrder(), selectedVideo.getId(), 
                            selectedVideo.getStatus(), selectedVideo.getVideoUrl(), selectionReason);
                } else {
                    String errorMsg = String.format("Scene %d (sceneId=%d, order=%d, summary='%s')", 
                            scene.getSceneOrder(), scene.getId(), scene.getSceneOrder(), scene.getSummary());
                    missingScenes.add(errorMsg);
                    log.warn("NO MERGEABLE VIDEO FOUND - sceneId={}, sceneOrder={} (COMPLETED or GENERATING with videoUrl 없음)", 
                            scene.getId(), scene.getSceneOrder());
                }
            }
            
            // 3. 병합 대상 최종 videoUrl 리스트 로그
            log.info("=== FINAL MERGE CANDIDATES ===");
            log.info("total videos found: {}", videoUrls.size());
            for (int i = 0; i < videoUrls.size(); i++) {
                log.info("mergeUrl[{}]: {}", i, videoUrls.get(i));
            }
            
            // 4. 영상 없는 Scene 처리
            if (!missingScenes.isEmpty()) {
                if (request.getSkipMissingVideos() != null && request.getSkipMissingVideos()) {
                    log.warn("Skipping scenes without videos: {}", missingScenes);
                } else {
                    String errorMsg = "다음 Scene에 영상이 없습니다: " + String.join(", ", missingScenes);
                    log.error("MERGE FAILED - missing scenes: {}", missingScenes);
                    throw new BusinessException(ErrorCode.VIDEO_MERGE_FAILED, errorMsg);
                }
            }
            
            if (videoUrls.isEmpty()) {
                throw new BusinessException(ErrorCode.VIDEO_MERGE_FAILED, "병합할 영상이 없습니다.");
            }
            
            // 5. FFmpeg으로 영상 병합
            String finalVideoUrl = mergeVideosWithFFmpeg(videoUrls, projectId);
            
            // 6. Project에 최종 영상 URL 저장
            project.setFinalVideoUrl(finalVideoUrl);
            projectRepository.save(project);
            
            log.info("=== PROJECT VIDEO MERGE COMPLETED ===");
            log.info("projectId={}, finalVideoUrl={}", projectId, finalVideoUrl);
            
            return ProjectVideoResponse.builder()
                    .projectId(projectId)
                    .finalVideoUrl(finalVideoUrl)
                    .status("READY")
                    .message("최종 영상 병합 완료")
                    .createdAt(project.getUpdatedAt())
                    .updatedAt(project.getUpdatedAt())
                    .build();
            
        } catch (BusinessException e) {
            log.error("Business exception during video merge - projectId={}", projectId, e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during video merge - projectId={}", projectId, e);
            throw new BusinessException(ErrorCode.VIDEO_MERGE_FAILED, "영상 병합에 실패했습니다: " + e.getMessage());
        }
    }
    
    @Override
    public ProjectVideoResponse getFinalVideo(Long projectId, String loginId) {
        log.info("Getting final video for projectId: {}, loginId: {}", projectId, loginId);
        
        // 권한 체크
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        
        if (!project.getUser().getLoginId().equals(loginId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        
        String finalVideoUrl = project.getFinalVideoUrl();
        String status = finalVideoUrl != null ? "READY" : "NOT_CREATED";
        String message = finalVideoUrl != null ? "최종 영상 있음" : "최종 영상 생성 전";
        
        return ProjectVideoResponse.builder()
                .projectId(projectId)
                .finalVideoUrl(finalVideoUrl)
                .status(status)
                .message(message)
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }
    
    /**
     * sceneId 기준으로 병합 가능한 가장 최신 영상 조회
     * 우선순위: 1. COMPLETED + videoUrl, 2. GENERATING + videoUrl
     * @param sceneId Scene ID
     * @return 병합 가능한 가장 최신 SceneVideo, 없으면 null
     */
    private SceneVideo getLatestMergeableSceneVideo(Long sceneId) {
        List<SceneVideo> videos = sceneVideoRepository.findBySceneIdOrderByCreatedAtDesc(sceneId);
        
        // 1순위: COMPLETED + videoUrl
        for (SceneVideo video : videos) {
            if (video.getStatus() == SceneVideo.VideoStatus.COMPLETED 
                    && video.getVideoUrl() != null 
                    && !video.getVideoUrl().trim().isEmpty()) {
                return video;
            }
        }
        
        // 2순위: GENERATING + videoUrl
        for (SceneVideo video : videos) {
            if (video.getStatus() == SceneVideo.VideoStatus.GENERATING 
                    && video.getVideoUrl() != null 
                    && !video.getVideoUrl().trim().isEmpty()) {
                return video;
            }
        }
        
        return null;
    }
    
    /**
     * FFmpeg으로 영상 병합
     */
    private String mergeVideosWithFFmpeg(List<String> videoUrls, Long projectId) throws IOException, InterruptedException {
        log.info("Merging {} videos with FFmpeg for projectId: {}", videoUrls.size(), projectId);
        
        // 출력 디렉토리 생성
        Path outputDir = Paths.get(videoOutputPath);
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }
        
        // 임시 파일 목록 생성
        List<String> tempFiles = new ArrayList<>();
        List<String> concatList = new ArrayList<>();
        
        for (int i = 0; i < videoUrls.size(); i++) {
            String videoUrl = videoUrls.get(i);
            String tempFile = downloadVideoToTemp(videoUrl, projectId, i);
            if (tempFile != null) {
                tempFiles.add(tempFile);
                concatList.add("file '" + tempFile + "'");
            }
        }
        
        if (concatList.isEmpty()) {
            throw new RuntimeException("No valid video files to merge");
        }
        
        // concat 파일 생성
        String concatFilePath = videoOutputPath + "/project-" + projectId + "-concat.txt";
        String concatContent = String.join("\n", concatList);
        Files.write(Paths.get(concatFilePath), concatContent.getBytes());
        
        // FFmpeg 명령어 생성
        String outputFileName = "project-" + projectId + "-final.mp4";
        String outputFilePath = videoOutputPath + "/" + outputFileName;
        
        // FFmpeg concat 명령어 (더 안정적인 방식)
        String[] ffmpegCommand = {
            "ffmpeg",
            "-f", "concat",
            "-safe", "0",
            "-i", concatFilePath,
            "-c", "copy",
            "-y",  // 파일 덮어쓰기
            outputFilePath
        };
        
        log.info("Executing FFmpeg command: {}", String.join(" ", ffmpegCommand));
        
        // FFmpeg 실행
        ProcessBuilder pb = new ProcessBuilder(ffmpegCommand);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        
        // 로그 읽기
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("FFmpeg: {}", line);
            }
        }
        
        int exitCode = process.waitFor();
        
        // 임시 파일 정리
        cleanupTempFiles(tempFiles, concatFilePath);
        
        if (exitCode != 0) {
            throw new RuntimeException("FFmpeg failed with exit code: " + exitCode);
        }
        
        // 최종 URL 생성
        String finalVideoUrl = videoBaseUrl + "/" + outputFileName;
        log.info("FFmpeg merge completed: {}", finalVideoUrl);
        
        return finalVideoUrl;
    }
    
    /**
     * 비디오 URL을 임시 파일로 다운로드
     */
    private String downloadVideoToTemp(String videoUrl, Long projectId, int index) {
        try {
            // MVP 기준: URL을 그대로 사용 (실제 다운로드은 별도 구현 필요)
            // 여기서는 URL을 파일명으로만 변환
            String fileName = "project-" + projectId + "-scene-" + index + ".mp4";
            String tempPath = videoOutputPath + "/" + fileName;
            
            log.info("Using video URL as temp file: {} -> {}", videoUrl, tempPath);
            
            // 실제 다운로드 로직은 주석 처리 (MVP 기준)
            // downloadFileFromUrl(videoUrl, tempPath);
            
            return tempPath;
        } catch (Exception e) {
            log.error("Failed to download video: {}", videoUrl, e);
            return null;
        }
    }
    
    /**
     * 임시 파일 정리
     */
    private void cleanupTempFiles(List<String> tempFiles, String concatFilePath) {
        for (String tempFile : tempFiles) {
            try {
                Files.deleteIfExists(Paths.get(tempFile));
            } catch (IOException e) {
                log.warn("Failed to delete temp file: {}", tempFile, e);
            }
        }
        
        try {
            Files.deleteIfExists(Paths.get(concatFilePath));
        } catch (IOException e) {
            log.warn("Failed to delete concat file: {}", concatFilePath, e);
        }
    }
}
