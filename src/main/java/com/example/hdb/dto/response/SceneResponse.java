package com.example.hdb.dto.response;

import com.example.hdb.entity.Scene;
import com.example.hdb.dto.common.OptionalElements;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SceneResponse {
    private Long id;
    private Long projectId;
    private Integer sceneOrder;
    private String summary;
    private String optionalElements;
    private OptionalElements optionalElementsObject; // JSON 객체 형태로 제공
    private String imagePrompt;
    private String videoPrompt;
    private String imageUrl; // 대표 이미지 URL (Service에서 계산됨)
    private String videoUrl; // 대표 영상 URL (Service에서 계산됨)
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // 대표 이미지 URL (가장 최신 이미지)
    private String representativeImageUrl;
    
    // 대표 영상 URL (가장 최신 영상)
    private String representativeVideoUrl;

    public static SceneResponse from(Scene scene) {
        return SceneResponse.from(scene, null, null, null);
    }

    public static SceneResponse from(Scene scene, OptionalElements optionalElementsObject) {
        return SceneResponse.from(scene, optionalElementsObject, null, null);
    }

    public static SceneResponse from(Scene scene, OptionalElements optionalElementsObject, String imageUrl, String videoUrl) {
        return SceneResponse.builder()
                .id(scene.getId())
                .projectId(scene.getProject() != null ? scene.getProject().getId() : null)
                .sceneOrder(scene.getSceneOrder())
                .summary(scene.getSummary())
                .optionalElements(scene.getOptionalElements()) // 원본 JSON 문자열
                .optionalElementsObject(optionalElementsObject) // Service에서 파싱한 객체
                .imagePrompt(scene.getImagePrompt())
                .videoPrompt(scene.getVideoPrompt())
                .imageUrl(imageUrl) // Service에서 계산된 대표 이미지 URL
                .videoUrl(videoUrl) // Service에서 계산된 대표 영상 URL
                .status(scene.getStatus().name())
                .createdAt(scene.getCreatedAt())
                .updatedAt(scene.getUpdatedAt())
                .representativeImageUrl(imageUrl) // 대표 이미지 URL과 동일
                .representativeVideoUrl(videoUrl) // 대표 영상 URL과 동일
                .build();
    }
}
