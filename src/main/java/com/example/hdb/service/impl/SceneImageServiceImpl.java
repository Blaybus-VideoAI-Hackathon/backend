package com.example.hdb.service.impl;

import com.example.hdb.dto.request.ImageEditCompleteRequest;
import com.example.hdb.dto.request.SceneImageEditAiRequest;
import com.example.hdb.dto.response.ImageGenerationResult;
import com.example.hdb.dto.response.SceneImageResponse;
import com.example.hdb.entity.Project;
import com.example.hdb.entity.Scene;
import com.example.hdb.entity.SceneImage;
import com.example.hdb.exception.BusinessException;
import com.example.hdb.exception.ErrorCode;
import com.example.hdb.repository.ProjectRepository;
import com.example.hdb.repository.SceneImageRepository;
import com.example.hdb.repository.SceneRepository;
import com.example.hdb.service.LeonardoAIService;
import com.example.hdb.service.OpenAIService;
import com.example.hdb.service.PlanningService;
import com.example.hdb.service.PromptEnhancerService;
import com.example.hdb.service.SceneImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class SceneImageServiceImpl implements SceneImageService {

    private final SceneImageRepository sceneImageRepository;
    private final SceneRepository sceneRepository;
    private final ProjectRepository projectRepository;
    private final LeonardoAIService leonardoAIService;
    private final OpenAIService openAIService;
    private final PlanningService planningService;
    private final PromptEnhancerService promptEnhancerService;
    private final RestTemplate restTemplate;

    @Value("${cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${cloudinary.api-key:}")
    private String apiKey;

    @Value("${cloudinary.api-secret:}")
    private String apiSecret;

    // ──────────────────────────────────────────
    // ★★★ 이미지 생성 (Leonardo AI 기반 일관성 보장) ★★★
    // ──────────────────────────────────────────

    @Override
    public SceneImageResponse generateImage(Long projectId, Long sceneId, String loginId) {
        log.info("=== SceneImageService.generateImage Started (Leonardo AI) ===");
        log.info("projectId: {}, sceneId: {}, loginId: {}", projectId, sceneId, loginId);

        Scene scene = sceneRepository.findByIdAndProjectId(sceneId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));

        validateProjectOwnership(scene.getProject(), loginId);

        String imagePrompt = scene.getImagePrompt();
        if (imagePrompt == null || imagePrompt.isBlank()) {
            log.error("Scene image prompt is null or empty");
            throw new BusinessException(ErrorCode.SCENE_IMAGE_PROMPT_NOT_FOUND);
        }

        // ★★★ 프롬프트 번역 + 보강 (한국어 → 영어 + Leonardo AI 최적화) ★★★
        Project project = scene.getProject();
        String enhancedPrompt = promptEnhancerService.enhancePrompt(
                imagePrompt,
                project.getStyle(),
                project.getRatio()
        );
        log.info("Original prompt: {}", truncate(imagePrompt, 150));
        log.info("Enhanced prompt: {}", truncate(enhancedPrompt, 300));

        Integer nextImageNumber = sceneImageRepository.findFirstBySceneIdOrderByImageNumberDesc(sceneId)
                .map(SceneImage::getImageNumber)
                .orElse(0) + 1;

        log.info("Next image number: {}", nextImageNumber);

        SceneImage sceneImage = SceneImage.builder()
                .scene(scene)
                .imageNumber(nextImageNumber)
                .imageUrl(null)
                .editedImageUrl(null)
                .imagePrompt(imagePrompt)
                .revisedPrompt(enhancedPrompt)
                .openaiImageId(null)
                .status(SceneImage.ImageStatus.GENERATING)
                .build();

        SceneImage savedImage = sceneImageRepository.save(sceneImage);
        log.info("SceneImage saved with GENERATING status: {}", savedImage.getId());

        try {
            // 첫 번째 씬인지 확인
            boolean isFirstScene = isFirstSceneInProject(sceneId, projectId);
            log.info("=== SCENE TYPE DETERMINED ===");
            log.info("Is first scene: {}", isFirstScene);

            ImageGenerationResult result;

            if (isFirstScene) {
                // ★★★ 경우 1: 첫 번째 씬 → 표준 Leonardo AI 생성 ★★★
                log.info("=== GENERATING FIRST SCENE (Standard Leonardo AI) ===");
                result = leonardoAIService.generateImage(enhancedPrompt, "STANDARD", null);
            } else {
                // ★★★ 경우 2: 두 번째 이후 씬 → 이미지 참조 API 호출 ★★★
                log.info("=== GENERATING SUBSEQUENT SCENE (Image Reference) ===");

                // 첫 번째 씬 이미지 찾기
                ProjectReferenceImage firstSceneImage = findFirstSceneImage(projectId);
                if (firstSceneImage == null || firstSceneImage.getImageUrl() == null) {
                    log.warn("No first scene image found for reference, using standard generation");
                    result = leonardoAIService.generateImage(enhancedPrompt, "STANDARD", null);
                } else {
                    log.info("Using first scene image as reference: {}", firstSceneImage.getImageUrl());

                    // 이미지 참조를 사용한 일관성 있는 이미지 생성
                    result = leonardoAIService.generateConsistentImage(
                            enhancedPrompt,
                            firstSceneImage.getImageUrl(),
                            firstSceneImage.getSeed()
                    );
                }
            }

            if (result.getImageUrl() == null || result.getImageUrl().isBlank()) {
                throw new RuntimeException("Leonardo AI returned empty imageUrl");
            }

            log.info("Generated image URL: {}", result.getImageUrl());
            log.info("Generated image seed: {}", result.getSeed());

            String permanentUrl = uploadUrlToCloudinary(result.getImageUrl(), sceneId, nextImageNumber, "generated");
            log.info("Cloudinary permanent URL: {}", permanentUrl);

            savedImage.setImageUrl(permanentUrl);
            savedImage.setRevisedPrompt(result.getRevisedPrompt());
            // ★ seed 또는 generation ID 저장 (추후 일관성 참조용)
            savedImage.setOpenaiImageId(result.getSeed() != null ? result.getSeed() : result.getGenerationId());
            savedImage.setStatus(SceneImage.ImageStatus.READY);
            SceneImage completedImage = sceneImageRepository.save(savedImage);

            log.info("IMAGE SAVED - sceneId: {}, imageId: {}, url: {}",
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

    /**
     * 프로젝트의 첫 번째 씬인지 확인
     */
    private boolean isFirstSceneInProject(Long sceneId, Long projectId) {
        try {
            List<Scene> scenes = sceneRepository.findByProjectIdOrderBySceneOrderAsc(projectId);
            if (!scenes.isEmpty()) {
                Scene firstScene = scenes.get(0);
                return firstScene.getId().equals(sceneId);
            }
            return false;
        } catch (Exception e) {
            log.warn("Failed to determine if scene is first", e);
            return false;
        }
    }

    /**
     * 프로젝트의 첫 번째 씬 이미지 찾기
     */
    private ProjectReferenceImage findFirstSceneImage(Long projectId) {
        try {
            List<Scene> scenes = sceneRepository.findByProjectIdOrderBySceneOrderAsc(projectId);
            if (!scenes.isEmpty()) {
                Scene firstScene = scenes.get(0);
                List<SceneImage> images = sceneImageRepository.findBySceneIdOrderByImageNumberAsc(firstScene.getId());
                if (!images.isEmpty()) {
                    SceneImage firstImage = images.get(0);
                    if (firstImage.getImageUrl() != null) {
                        log.info("Found first scene image from scene {} (order {})",
                                firstScene.getId(), firstScene.getSceneOrder());

                        return ProjectReferenceImage.builder()
                                .imageUrl(firstImage.getImageUrl())
                                .seed(firstImage.getOpenaiImageId())
                                .build();
                    }
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("Failed to find first scene image", e);
            return null;
        }
    }

    @Override
    public List<SceneImageResponse> getImages(Long projectId, Long sceneId, String loginId) {
        log.info("Getting images for scene: {} in project: {}", sceneId, projectId);

        Scene scene = sceneRepository.findByIdAndProjectId(sceneId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));

        validateProjectOwnership(scene.getProject(), loginId);

        List<SceneImage> images = sceneImageRepository.findBySceneIdOrderByImageNumberAsc(sceneId);
        return images.stream()
                .map(img -> toResponse(img, isFallbackUrl(img.getImageUrl())))
                .collect(Collectors.toList());
    }

    @Override
    public List<SceneImageResponse> getProjectImages(Long projectId, String loginId) {
        log.info("Getting all images for project: {}, user: {}", projectId, loginId);

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        validateProjectOwnership(project, loginId);

        List<SceneImage> images = sceneImageRepository.findBySceneProjectIdOrderBySceneSceneOrderAscImageNumberAsc(projectId);
        return images.stream()
                .map(img -> toResponse(img, isFallbackUrl(img.getImageUrl())))
                .collect(Collectors.toList());
    }

    /**
     * 일관성 있는 이미지 생성
     */
    public SceneImageResponse generateConsistentImage(
            Long projectId,
            Long sceneId,
            String referenceImageUrl,
            String imagePrompt,
            String loginId) {

        log.info("=== GenerateConsistentImage Started ===");
        log.info("projectId: {}, sceneId: {}", projectId, sceneId);

        Scene scene = sceneRepository.findByIdAndProjectId(sceneId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));

        validateProjectOwnership(scene.getProject(), loginId);

        if (referenceImageUrl == null || referenceImageUrl.isBlank()) {
            throw new BusinessException(ErrorCode.SCENE_IMAGE_PROMPT_NOT_FOUND);
        }

        if (imagePrompt == null || imagePrompt.isBlank()) {
            throw new BusinessException(ErrorCode.SCENE_IMAGE_PROMPT_NOT_FOUND);
        }

        // ★★★ 프롬프트 번역 + 보강 (한국어 → 영어 + Leonardo AI 최적화) ★★★
        Project project = scene.getProject();
        String enhancedPrompt = promptEnhancerService.enhancePrompt(
                imagePrompt,
                project.getStyle(),
                project.getRatio()
        );
        log.info("Original prompt: {}", truncate(imagePrompt, 150));
        log.info("Enhanced prompt: {}", truncate(enhancedPrompt, 300));

        Integer nextImageNumber = sceneImageRepository.findFirstBySceneIdOrderByImageNumberDesc(sceneId)
                .map(SceneImage::getImageNumber)
                .orElse(0) + 1;

        SceneImage sceneImage = SceneImage.builder()
                .scene(scene)
                .imageNumber(nextImageNumber)
                .imageUrl(null)
                .imagePrompt(imagePrompt)
                .revisedPrompt(enhancedPrompt)
                .status(SceneImage.ImageStatus.GENERATING)
                .build();

        SceneImage savedImage = sceneImageRepository.save(sceneImage);

        try {
            ImageGenerationResult result = leonardoAIService.generateConsistentImage(
                    enhancedPrompt,
                    referenceImageUrl,
                    null
            );

            if (result == null || result.getImageUrl() == null) {
                savedImage.setStatus(SceneImage.ImageStatus.FAILED);
                sceneImageRepository.save(savedImage);
                throw new BusinessException(ErrorCode.IMAGE_GENERATION_FAILED);
            }

            String cloudinaryUrl = uploadUrlToCloudinary(result.getImageUrl(), sceneId, nextImageNumber, "consistent");
            savedImage.setImageUrl(cloudinaryUrl);
            savedImage.setOpenaiImageId(result.getSeed() != null ? result.getSeed().toString() : null);
            savedImage.setStatus(SceneImage.ImageStatus.READY);
            sceneImageRepository.save(savedImage);

            return toResponse(savedImage, false);

        } catch (Exception e) {
            log.error("Failed to generate consistent image", e);
            savedImage.setStatus(SceneImage.ImageStatus.FAILED);
            sceneImageRepository.save(savedImage);
            throw new BusinessException(ErrorCode.IMAGE_GENERATION_FAILED);
        }
    }

    @Override
    public SceneImageResponse completeImageEdit(Long projectId, Long sceneId, Long imageId, String loginId, ImageEditCompleteRequest request) {
        log.info("Completing image edit for imageId: {}, projectId: {}, sceneId: {}", imageId, projectId, sceneId);

        Scene scene = sceneRepository.findByIdAndProjectId(sceneId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));

        validateProjectOwnership(scene.getProject(), loginId);

        SceneImage sceneImage = sceneImageRepository.findByIdAndSceneId(imageId, sceneId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_IMAGE_NOT_FOUND));

        if (request.getEditedImageBase64() != null && !request.getEditedImageBase64().isBlank()) {
            byte[] imageBytes = Base64.getDecoder().decode(request.getEditedImageBase64());
            String permanentUrl = uploadBytesToCloudinary(imageBytes, sceneId, sceneImage.getImageNumber(), "edited", "png");
            sceneImage.setEditedImageUrl(permanentUrl);
        }

        if (request.getEditNotes() != null && !request.getEditNotes().isBlank()) {
            log.info("Edit notes: {}", request.getEditNotes());
        }

        SceneImage updated = sceneImageRepository.save(sceneImage);
        return toResponse(updated, false);
    }

    @Override
    public SceneImageResponse generateImageEditAi(Long projectId, Long sceneId, Long imageId, String loginId, SceneImageEditAiRequest request) {
        log.info("=== AI Image Edit Generation Started ===");
        log.info("projectId: {}, sceneId: {}, imageId: {}, userEditText: {}",
                projectId, sceneId, imageId, request.getUserEditText());

        Scene scene = sceneRepository.findByIdAndProjectId(sceneId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));

        validateProjectOwnership(scene.getProject(), loginId);

        SceneImage originalImage = sceneImageRepository.findByIdAndSceneId(imageId, sceneId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_IMAGE_NOT_FOUND));

        String originalPrompt = originalImage.getImagePrompt();
        String editedPrompt = buildEditedPrompt(originalPrompt, request.getUserEditText());

        // ★★★ 편집 프롬프트도 번역 + 보강 ★★★
        Project project = scene.getProject();
        String enhancedEditPrompt = promptEnhancerService.enhancePrompt(
                editedPrompt,
                project.getStyle(),
                project.getRatio()
        );

        log.info("Original prompt: {}", truncate(originalPrompt, 100));
        log.info("Edited prompt: {}", truncate(editedPrompt, 100));
        log.info("Enhanced edit prompt: {}", truncate(enhancedEditPrompt, 200));

        Integer nextImageNumber = sceneImageRepository.findFirstBySceneIdOrderByImageNumberDesc(sceneId)
                .map(SceneImage::getImageNumber)
                .orElse(0) + 1;

        SceneImage newImage = SceneImage.builder()
                .scene(scene)
                .imageNumber(nextImageNumber)
                .imageUrl(null)
                .editedImageUrl(null)
                .imagePrompt(editedPrompt)
                .revisedPrompt(null)
                .openaiImageId(null)
                .status(SceneImage.ImageStatus.GENERATING)
                .build();

        SceneImage savedImage = sceneImageRepository.save(newImage);

        try {
            ImageGenerationResult result = leonardoAIService.generateImage(enhancedEditPrompt, "STANDARD", null);

            if (result.getImageUrl() == null || result.getImageUrl().isBlank()) {
                throw new RuntimeException("Leonardo AI returned empty imageUrl for edit");
            }

            String permanentUrl = uploadUrlToCloudinary(result.getImageUrl(), sceneId, nextImageNumber, "edited");

            savedImage.setImageUrl(permanentUrl);
            savedImage.setRevisedPrompt(result.getRevisedPrompt());
            savedImage.setOpenaiImageId(result.getSeed() != null ? result.getSeed() : result.getGenerationId());
            savedImage.setStatus(SceneImage.ImageStatus.READY);
            SceneImage completedImage = sceneImageRepository.save(savedImage);

            log.info("AI EDIT IMAGE COMPLETED - sceneId: {}, imageId: {}", sceneId, completedImage.getId());
            return toResponse(completedImage, false);

        } catch (Exception e) {
            log.error("AI image edit generation failed", e);

            String fallbackUrl = generateFallbackImageUrl(editedPrompt);
            savedImage.setImageUrl(fallbackUrl);
            savedImage.setStatus(SceneImage.ImageStatus.READY);
            SceneImage completedImage = sceneImageRepository.save(savedImage);

            return toResponse(completedImage, true);
        }
    }

    private String buildConsistentStylePrefix(Project project) {
        String style = project.getStyle() != null ? project.getStyle() : "modern animation";
        return "In the style of " + style + ", matching consistent visual aesthetic";
    }

    private String buildConsistentImagePrompt(Scene scene, String consistentStylePrefix) {
        if (scene.getImagePrompt() == null || scene.getImagePrompt().isBlank()) {
            return null;
        }

        StringBuilder sceneSpecificPrompt = new StringBuilder(scene.getImagePrompt());

        if (scene.getSummary() != null && !scene.getSummary().isBlank()) {
            sceneSpecificPrompt.append(", ").append(scene.getSummary());
        }

        sceneSpecificPrompt.append(", cinematic composition, detailed, high quality");

        String finalPrompt = consistentStylePrefix + " | Scene: " + sceneSpecificPrompt.toString();

        return sanitizePrompt(finalPrompt);
    }

    private String uploadUrlToCloudinary(String imageUrl, Long sceneId, int imageNumber, String suffix) {
        log.info("=== Uploading URL to Cloudinary ===");

        if (cloudName == null || cloudName.isBlank()) {
            log.warn("Cloudinary not configured, using original URL");
            return imageUrl;
        }

        try {
            String uploadUrl = String.format("https://api.cloudinary.com/v1_1/%s/image/upload", cloudName);

            long timestamp = System.currentTimeMillis() / 1000;
            String publicId = String.format("hdb/scene_%d_img_%d_%s_%d", sceneId, imageNumber, suffix, timestamp);

            String signatureInput = String.format("public_id=%s&timestamp=%d%s", publicId, timestamp, apiSecret);
            String signature = sha1Hex(signatureInput);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", imageUrl);
            body.add("api_key", apiKey);
            body.add("timestamp", String.valueOf(timestamp));
            body.add("public_id", publicId);
            body.add("signature", signature);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(uploadUrl, requestEntity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object secureUrl = response.getBody().get("secure_url");
                if (secureUrl != null && !secureUrl.toString().isBlank()) {
                    log.info("Cloudinary upload SUCCESS - url: {}", secureUrl);
                    return secureUrl.toString();
                }
            }

            throw new RuntimeException("Cloudinary upload failed");

        } catch (Exception e) {
            throw new RuntimeException("Cloudinary upload error", e);
        }
    }

    private String uploadBytesToCloudinary(byte[] imageBytes, Long sceneId, int imageNumber, String suffix, String extension) {
        log.info("=== Uploading BYTES to Cloudinary ===");

        if (cloudName == null || cloudName.isBlank()) {
            throw new RuntimeException("Cloudinary is not configured");
        }

        try {
            String uploadUrl = String.format("https://api.cloudinary.com/v1_1/%s/image/upload", cloudName);

            long timestamp = System.currentTimeMillis() / 1000;
            String publicId = String.format("hdb/scene_%d_img_%d_%s_%d", sceneId, imageNumber, suffix, timestamp);

            String signatureInput = String.format("public_id=%s&timestamp=%d%s", publicId, timestamp, apiSecret);
            String signature = sha1Hex(signatureInput);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            ByteArrayResource resource = new ByteArrayResource(imageBytes) {
                @Override
                public String getFilename() {
                    return "edited-image." + extension;
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", resource);
            body.add("api_key", apiKey);
            body.add("timestamp", String.valueOf(timestamp));
            body.add("public_id", publicId);
            body.add("signature", signature);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(uploadUrl, requestEntity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object secureUrl = response.getBody().get("secure_url");
                if (secureUrl != null && !secureUrl.toString().isBlank()) {
                    log.info("Cloudinary upload SUCCESS - url: {}", secureUrl);
                    return secureUrl.toString();
                }
            }

            throw new RuntimeException("Cloudinary byte upload failed");

        } catch (Exception e) {
            throw new RuntimeException("Cloudinary byte upload error", e);
        }
    }

    private String sha1Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-1 hashing failed", e);
        }
    }

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
                .fallbackUsed(fallbackUsed)
                .isEdited(image.getEditedImageUrl() != null && !image.getEditedImageUrl().isBlank())
                .build();

        return response;
    }

    private String sanitizePrompt(String prompt) {
        if (prompt == null) return "";
        return prompt.replace("null", " ").replaceAll("\\s+", " ").trim();
    }

    private String buildEditedPrompt(String originalPrompt, String userEditText) {
        String base = (originalPrompt != null && !originalPrompt.isBlank()) ? originalPrompt : "원본 장면";
        return sanitizePrompt(base + ", 수정 요청 반영: " + userEditText);
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

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }

    /**
     * 강력한 프롬프트 생성
     */
    private String generateStrongPrompt(String originalPrompt, Project project) {
        try {
            String style = project.getStyle() != null ? project.getStyle() : "illustration";

            return String.format(
                    "A beautiful illustration of a happy young child, "
                            + "soft warm lighting, "
                            + "cozy setting with detailed background, "
                            + "warm and inviting atmosphere, "
                            + "child with bright cheerful expression, "
                            + "high quality professional illustration, "
                            + "detailed facial features, "
                            + "%s style, "
                            + "vibrant warm colors, "
                            + "masterpiece quality, "
                            + "cinematic lighting, "
                            + "4k, highly detailed",
                    style
            );
        } catch (Exception e) {
            log.warn("Failed to generate strong prompt: {}", e.getMessage());
            return "beautiful illustration of a happy child, warm lighting, detailed, professional, masterpiece";
        }
    }

    @lombok.Data
    @lombok.Builder
    private static class ProjectReferenceImage {
        private String imageUrl;
        private String seed;
    }
}