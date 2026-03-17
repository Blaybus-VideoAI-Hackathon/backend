package com.example.hdb.dto.response;

import com.example.hdb.entity.Scene;
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
    private String imagePrompt;
    private String videoPrompt;
    private String imageUrl;
    private String videoUrl;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SceneResponse from(Scene scene) {
        return SceneResponse.builder()
                .id(scene.getId())
                .projectId(scene.getProject() != null ? scene.getProject().getId() : null)
                .sceneOrder(scene.getSceneOrder())
                .summary(scene.getSummary())
                .optionalElements(scene.getOptionalElements())
                .imagePrompt(scene.getImagePrompt())
                .videoPrompt(scene.getVideoPrompt())
                .imageUrl(scene.getImageUrl())
                .videoUrl(scene.getVideoUrl())
                .status(scene.getStatus().name())
                .createdAt(scene.getCreatedAt())
                .updatedAt(scene.getUpdatedAt())
                .build();
    }
}
