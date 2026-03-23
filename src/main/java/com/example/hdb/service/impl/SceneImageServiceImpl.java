package com.example.hdb.service.impl;

import com.example.hdb.dto.request.ImageEditCompleteRequest;
import com.example.hdb.dto.request.SceneImageEditAiRequest;
import com.example.hdb.dto.response.SceneImageResponse;
import com.example.hdb.entity.Project;
import com.example.hdb.entity.Scene;
import com.example.hdb.entity.SceneImage;
import com.example.hdb.exception.BusinessException;
import com.example.hdb.exception.ErrorCode;
import com.example.hdb.repository.ProjectRepository;
import com.example.hdb.repository.SceneImageRepository;
import com.example.hdb.repository.SceneRepository;
import com.example.hdb.service.OpenAIService;
import com.example.hdb.service.SceneImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
public class SceneImageServiceImpl implements SceneImageService {

    private final SceneImageRepository sceneImageRepository;
    private final SceneRepository sceneRepository;
    private final ProjectRepository projectRepository;
    private final OpenAIService openAIService;
    private final RestTemplate restTemplate;

    @Value("${cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${cloudinary.api-key:}")
    private String apiKey;

    @Value("${cloudinary.api-secret:}")
    private String apiSecret;

    public SceneImageServiceImpl(
            SceneImageRepository sceneImageRepository,
            SceneRepository sceneRepository,
            ProjectRepository projectRepository,
            OpenAIService openAIService,
            RestTemplate restTemplate
    ) {
        this.sceneImageRepository = sceneImageRepository;
        this.sceneRepository = sceneRepository;
        this.projectRepository = projectRepository;
        this.openAIService = openAIService;
        this.restTemplate = restTemplate;
    }

    // ──────────────────────────────────────────
    // 이미지 생성
    // ──────────────────────────────────────────

    @Override
    public SceneImageResponse generateImage(Long projectId, Long sceneId, String loginId) {
        log.info("=== SceneImageService.generateImage Started ===");
        log.info("projectId: {}, sceneId: {}, loginId: {}", projectId, sceneId, loginId);

        Scene scene = sceneRepository.findByIdAndProjectId(sceneId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));

        validateProjectOwnership(scene.getProject(), loginId);

        String imagePrompt = buildRealImagePrompt(scene);
        if (imagePrompt == null || imagePrompt.isBlank()) {
            log.error("Scene image prompt is null or empty after normalization");
            throw new BusinessException(ErrorCode.SCENE_IMAGE_PROMPT_NOT_FOUND);
        }

        Integer nextImageNumber = sceneImageRepository.findFirstBySceneIdOrderByImageNumberDesc(sceneId)
                .map(SceneImage::getImageNumber)
                .orElse(0) + 1;

        log.info("Next image number: {}", nextImageNumber);
        log.info("Final image prompt: {}", imagePrompt);

        SceneImage sceneImage = SceneImage.builder()
                .scene(scene)
                .imageNumber(nextImageNumber)
                .imageUrl(null)
                .editedImageUrl(null)
                .imagePrompt(imagePrompt)
                .openaiImageId(null)
                .status(SceneImage.ImageStatus.GENERATING)
                .build();

        SceneImage savedImage = sceneImageRepository.save(sceneImage);
        log.info("SceneImage saved with GENERATING status: {}", savedImage.getId());

        try {
            // 1. OpenAI DALL-E로 이미지 생성 (임시 URL)
            log.info("=== CALLING OPENAI IMAGE API ===");
            String tempImageUrl = openAIService.generateImage(imagePrompt);

            if (tempImageUrl == null || tempImageUrl.isBlank()) {
                throw new RuntimeException("AI returned empty imageUrl");
            }
            log.info("OpenAI temp URL: {}", tempImageUrl);

            // 2. Cloudinary에 업로드해서 영구 URL로 변환
            String permanentUrl = uploadToCloudinary(tempImageUrl, sceneId, nextImageNumber);
            log.info("Cloudinary permanent URL: {}", permanentUrl);

            savedImage.setImageUrl(permanentUrl);
            savedImage.setStatus(SceneImage.ImageStatus.READY);
            SceneImage completedImage = sceneImageRepository.save(savedImage);

            log.info("IMAGE SAVED WITH PERMANENT URL - sceneId: {}, imageId: {}, url: {}",
                    sceneId, completedImage.getId(), completedImage.getImageUrl());

            return toResponse(completedImage, false);

        } catch (Exception e) {
            log.error("=== IMAGE GENERATION FAILED, USING FALLBACK ===", e);

            String fallbackImageUrl = generateFallbackImageUrl(imagePrompt);
            savedImage.setImageUrl(fallbackImageUrl);
            savedImage.setStatus(SceneImage.ImageStatus.READY);
            SceneImage completedImage = sceneImageRepository.save(savedImage);

            log.warn("IMAGE FALLBACK COMPLETED - sceneId: {}, imageId: {}, fallbackUrl: {}",
                    sceneId, completedImage.getId(), completedImage.getImageUrl());

            return toResponse(completedImage, true);
        }
    }

    // ──────────────────────────────────────────
    // 조회
    // ──────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<SceneImageResponse> getImages(Long projectId, Long sceneId, String loginId) {
        log.info("Getting images for scene: {}, project: {}, user: {}", sceneId, projectId, loginId);

        Scene scene = sceneRepository.findByIdAndProjectId(sceneId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));

        validateProjectOwnership(scene.getProject(), loginId);

        List<SceneImage> images = sceneImageRepository.findBySceneIdOrderByImageNumberAsc(sceneId);

        return images.stream()
                .map(image -> toResponse(image, isFallbackUrl(image.getImageUrl())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SceneImageResponse> getProjectImages(Long projectId, String loginId) {
        log.info("Getting all images for project: {}, user: {}", projectId, loginId);

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        validateProjectOwnership(project, loginId);

        List<Scene> scenes = sceneRepository.findByProjectIdOrderBySceneOrderAsc(projectId);

        List<SceneImage> allImages = scenes.stream()
                .flatMap(scene -> sceneImageRepository.findBySceneIdOrderByImageNumberAsc(scene.getId()).stream())
                .collect(Collectors.toList());

        log.info("Found {} images for project: {}", allImages.size(), projectId);

        return allImages.stream()
                .map(image -> {
                    SceneImageResponse response = toResponse(image, isFallbackUrl(image.getImageUrl()));
                    response.setSceneId(image.getScene().getId());
                    return response;
                })
                .collect(Collectors.toList());
    }

    // ──────────────────────────────────────────
    // 이미지 편집
    // ──────────────────────────────────────────

    @Override
    public SceneImageResponse completeImageEdit(Long projectId, Long sceneId, Long imageId,
                                                String loginId, ImageEditCompleteRequest request) {
        log.info("Completing image edit for imageId: {}", imageId);

        SceneImage sceneImage = sceneImageRepository.findByIdAndSceneId(imageId, sceneId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IMAGE_NOT_FOUND));

        Scene scene = sceneImage.getScene();
        validateProjectOwnership(scene.getProject(), loginId);

        sceneImage.setEditedImageUrl(request.getEditedImageUrl());
        SceneImage savedImage = sceneImageRepository.save(sceneImage);

        log.info("Image edit completed successfully: {}", savedImage.getId());

        return toResponse(savedImage, isFallbackUrl(savedImage.getImageUrl()));
    }

    @Override
    public SceneImageResponse generateImageEditAi(Long projectId, Long sceneId, Long imageId,
                                                  String loginId, SceneImageEditAiRequest request) {
        log.info("=== AI Image Edit Generation Started ===");
        log.info("projectId: {}, sceneId: {}, imageId: {}, userEditText: {}",
                projectId, sceneId, imageId, request.getUserEditText());

        SceneImage originalImage = sceneImageRepository.findByIdAndSceneId(imageId, sceneId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IMAGE_NOT_FOUND));

        Scene scene = originalImage.getScene();
        validateProjectOwnership(scene.getProject(), loginId);

        String sourceImageUrl = originalImage.getEditedImageUrl() != null && !originalImage.getEditedImageUrl().isBlank()
                ? originalImage.getEditedImageUrl()
                : originalImage.getImageUrl();

        Integer maxImageNumber = sceneImageRepository.findMaxImageNumberBySceneId(sceneId);
        int nextImageNumber = (maxImageNumber != null ? maxImageNumber : 0) + 1;

        log.info("Source image URL: {}", sourceImageUrl);
        log.info("Next image number: {}", nextImageNumber);

        try {
            String editedImageUrl = generateEditedImageUrl(sourceImageUrl, request.getUserEditText());
            String newImagePrompt = buildEditedPrompt(originalImage.getImagePrompt(), request.getUserEditText());

            SceneImage newImage = SceneImage.builder()
                    .scene(scene)
                    .imageNumber(nextImageNumber)
                    .imageUrl(sourceImageUrl)
                    .editedImageUrl(editedImageUrl)
                    .imagePrompt(newImagePrompt)
                    .openaiImageId(null)
                    .status(SceneImage.ImageStatus.READY)
                    .build();

            SceneImage savedImage = sceneImageRepository.save(newImage);
            log.info("Saved new edited image with ID: {}", savedImage.getId());

            return toResponse(savedImage, false);

        } catch (Exception e) {
            log.warn("AI image edit failed, using fallback: {}", e.getMessage(), e);

            String fallbackEditedUrl = generateFallbackEditedUrl(sourceImageUrl, request.getUserEditText());
            String newImagePrompt = buildEditedPrompt(originalImage.getImagePrompt(), request.getUserEditText()) + " (fallback)";

            SceneImage newImage = SceneImage.builder()
                    .scene(scene)
                    .imageNumber(nextImageNumber)
                    .imageUrl(sourceImageUrl)
                    .editedImageUrl(fallbackEditedUrl)
                    .imagePrompt(newImagePrompt)
                    .openaiImageId(null)
                    .status(SceneImage.ImageStatus.READY)
                    .build();

            SceneImage savedImage = sceneImageRepository.save(newImage);
            log.info("Saved fallback edited image with ID: {}", savedImage.getId());

            return toResponse(savedImage, true);
        }
    }

    // ──────────────────────────────────────────
    // PRIVATE: Cloudinary 업로드
    // ──────────────────────────────────────────

    /**
     * OpenAI 임시 URL → Cloudinary 영구 URL로 변환
     * Cloudinary REST API 직접 호출 (SDK 없이)
     * 참고: https://cloudinary.com/documentation/image_upload_api_reference
     */
    private String uploadToCloudinary(String imageUrl, Long sceneId, int imageNumber) {
        log.info("=== Uploading to Cloudinary ===");
        log.info("cloudName={}, imageUrl={}", cloudName, imageUrl);

        if (cloudName == null || cloudName.isBlank()) {
            log.warn("Cloudinary not configured, using original URL");
            return imageUrl;
        }

        try {
            // Cloudinary unsigned upload (signed upload으로 변경 가능)
            // https://api.cloudinary.com/v1_1/{cloud_name}/image/upload
            String uploadUrl = String.format(
                    "https://api.cloudinary.com/v1_1/%s/image/upload", cloudName);

            // 타임스탬프 기반 서명 생성
            long timestamp = System.currentTimeMillis() / 1000;
            String publicId = String.format("hdb/scene_%d_img_%d_%d", sceneId, imageNumber, timestamp);

            // 서명 생성: SHA-1(public_id=...&timestamp=...{api_secret})
            String signatureInput = String.format(
                    "public_id=%s&timestamp=%d%s", publicId, timestamp, apiSecret);
            String signature = sha1Hex(signatureInput);

            // multipart/form-data 요청
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("file", imageUrl);           // URL로 업로드
            body.add("upload_preset", "");        // preset 없으면 빈 값
            body.add("api_key", apiKey);
            body.add("timestamp", String.valueOf(timestamp));
            body.add("public_id", publicId);
            body.add("signature", signature);

            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    uploadUrl, requestEntity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object secureUrl = response.getBody().get("secure_url");
                if (secureUrl != null && !secureUrl.toString().isBlank()) {
                    log.info("Cloudinary upload SUCCESS - url: {}", secureUrl);
                    return secureUrl.toString();
                }
            }

            log.warn("Cloudinary upload failed, using original URL. status={}",
                    response.getStatusCode());
            return imageUrl;

        } catch (Exception e) {
            log.warn("Cloudinary upload error, using original URL: {}", e.getMessage());
            return imageUrl;
        }
    }

    /**
     * SHA-1 해시 생성 (Cloudinary 서명용)
     */
    private String sha1Hex(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            byte[] bytes = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-1 hashing failed", e);
        }
    }

    // ──────────────────────────────────────────
    // PRIVATE: 기타 헬퍼
    // ──────────────────────────────────────────

    private void validateProjectOwnership(Project project, String loginId) {
        if (!project.getUser().getLoginId().equals(loginId)) {
            log.error("Unauthorized access: projectId={}, loginId={}, owner={}",
                    project.getId(), loginId, project.getUser().getLoginId());
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
    }

    private SceneImageResponse toResponse(SceneImage image, boolean fallbackUsed) {
        SceneImageResponse response = SceneImageResponse.builder()
                .id(image.getId())
                .sceneId(image.getScene().getId())
                .imageNumber(image.getImageNumber())
                .imageUrl(image.getImageUrl())
                .editedImageUrl(image.getEditedImageUrl())
                .imagePrompt(image.getImagePrompt())
                .status(image.getStatus().name())
                .statusDescription(image.getStatus().getDescription())
                .createdAt(image.getCreatedAt())
                .updatedAt(image.getUpdatedAt())
                .build();

        response.setFallbackUsed(fallbackUsed);
        return response;
    }

    private String buildRealImagePrompt(Scene scene) {
        String rawPrompt = scene.getImagePrompt();
        String summary = scene.getSummary() != null ? scene.getSummary().trim() : "";
        String optionalElements = scene.getOptionalElements() != null
                ? scene.getOptionalElements().trim() : "";

        boolean genericPrompt = rawPrompt == null
                || rawPrompt.isBlank()
                || rawPrompt.toLowerCase().contains("standard scene")
                || rawPrompt.toLowerCase().contains("basic lighting")
                || rawPrompt.toLowerCase().contains("basic composition");

        if (!genericPrompt) {
            return sanitizePrompt(rawPrompt);
        }

        StringBuilder prompt = new StringBuilder();
        if (!summary.isBlank()) {
            prompt.append(summary).append(", ");
        }
        if (!optionalElements.isBlank() && !"{}".equals(optionalElements)) {
            prompt.append("scene design: ").append(optionalElements).append(", ");
        }
        prompt.append("warm soft lighting, cinematic composition, high detail, 4k");

        return sanitizePrompt(prompt.toString());
    }

    private String sanitizePrompt(String prompt) {
        if (prompt == null) return "";
        return prompt.replace("null", " ").replaceAll("\\s+", " ").trim();
    }

    private String buildEditedPrompt(String originalPrompt, String userEditText) {
        String base = originalPrompt != null && !originalPrompt.isBlank()
                ? originalPrompt : "A scene";
        return sanitizePrompt(base + ", edited: " + userEditText);
    }

    private String generateEditedImageUrl(String sourceUrl, String userEditText) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new RuntimeException("Source image URL is empty");
        }
        return String.format("%s?edited=%d&prompt=%d",
                sourceUrl, System.currentTimeMillis(), userEditText.hashCode());
    }

    private String generateFallbackEditedUrl(String sourceUrl, String userEditText) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return String.format("https://fallback-image-service.com/edited/%d_%d.png",
                    System.currentTimeMillis(), userEditText.hashCode());
        }
        return String.format("%s?fallback=%d&prompt=%d",
                sourceUrl, System.currentTimeMillis(), userEditText.hashCode());
    }

    private String generateFallbackImageUrl(String imagePrompt) {
        log.warn("IMAGE FALLBACK USED - imagePrompt: {}", imagePrompt);
        return String.format("https://fallback-image-service.com/generated/%d_%d.png",
                System.currentTimeMillis(), imagePrompt.hashCode());
    }

    private boolean isFallbackUrl(String imageUrl) {
        if (imageUrl == null) return false;
        return imageUrl.contains("fallback-image-service.com")
                || imageUrl.contains("picsum.photos");
    }
}