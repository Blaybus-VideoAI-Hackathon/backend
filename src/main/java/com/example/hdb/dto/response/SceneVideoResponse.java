package com.example.hdb.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class SceneVideoResponse {
    
    private Long id;
    private Long sceneId;
    private Integer sceneOrder;
    private Integer duration;
    private String videoUrl;
    private String videoPrompt;
    private String status;
    private String statusDescription;
    private Boolean representative;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
