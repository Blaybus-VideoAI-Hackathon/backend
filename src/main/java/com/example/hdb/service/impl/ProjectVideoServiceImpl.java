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
        log.info("Merging videos for projectId: {}, loginId: {}", projectId, loginId);
        
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
            
            // 2. 각 Scene의 영상 URL 수집
            List<String> videoUrls = new ArrayList<>();
            List<String> missingScenes = new ArrayList<>();
            
            for (Scene scene : scenes) {
                String videoUrl = getSceneVideoUrl(scene);
                if (videoUrl != null && !videoUrl.trim().isEmpty()) {
                    videoUrls.add(videoUrl);
                } else {
                    missingScenes.add("Scene " + scene.getSceneOrder() + " (" + scene.getSummary() + ")");
                }
            }
            
            // 3. 영상 없는 Scene 처리
            if (!missingScenes.isEmpty()) {
                if (request.getSkipMissingVideos() != null && request.getSkipMissingVideos()) {
                    log.warn("Skipping scenes without videos: {}", missingScenes);
                } else {
                    String errorMsg = "다음 Scene에 영상이 없습니다: " + String.join(", ", missingScenes);
                    throw new BusinessException(ErrorCode.VIDEO_MERGE_FAILED, errorMsg);
                }
            }
            
            if (videoUrls.isEmpty()) {
                throw new BusinessException(ErrorCode.VIDEO_MERGE_FAILED, "병합할 영상이 없습니다.");
            }
            
            // 4. FFmpeg으로 영상 병합
            String finalVideoUrl = mergeVideosWithFFmpeg(videoUrls, projectId);
            
            // 5. Project에 최종 영상 URL 저장
            project.setFinalVideoUrl(finalVideoUrl);
            projectRepository.save(project);
            
            log.info("Video merge completed successfully: projectId={}, finalVideoUrl={}", projectId, finalVideoUrl);
            
            return ProjectVideoResponse.builder()
                    .projectId(projectId)
                    .finalVideoUrl(finalVideoUrl)
                    .status("READY")
                    .message("최종 영상 병합 완료")
                    .createdAt(project.getUpdatedAt())
                    .updatedAt(project.getUpdatedAt())
                    .build();
            
        } catch (Exception e) {
            log.error("Failed to merge videos for projectId: {}", projectId, e);
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
     * Scene의 영상 URL 조회 (우선순위: Scene.editedImageUrl -> SceneImage.editedImageUrl -> SceneImage.imageUrl -> Scene.videoUrl)
     */
    private String getSceneVideoUrl(Scene scene) {
        // 1. Scene.editedImageUrl
        if (scene.getEditedImageUrl() != null && !scene.getEditedImageUrl().trim().isEmpty()) {
            return scene.getEditedImageUrl();
        }
        
        // 2. SceneImage 중 가장 최신 영상
        List<SceneVideo> videos = sceneVideoRepository.findBySceneOrderByCreatedAtDesc(scene);
        if (!videos.isEmpty()) {
            SceneVideo latestVideo = videos.get(0);
            if (latestVideo.getVideoUrl() != null && !latestVideo.getVideoUrl().trim().isEmpty()) {
                return latestVideo.getVideoUrl();
            }
        }
        
        // 3. Scene.videoUrl (기존 필드)
        if (scene.getVideoUrl() != null && !scene.getVideoUrl().trim().isEmpty()) {
            return scene.getVideoUrl();
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
