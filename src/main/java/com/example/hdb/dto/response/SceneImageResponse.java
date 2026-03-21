package com.example.hdb.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class SceneImageResponse {
    
    private Long id;
    private Long sceneId;
    private Integer imageNumber;
    private String imageUrl;
    private String editedImageUrl;
    private String imagePrompt;
    private String status;
    private String statusDescription;
    private LocalDateTime createdAt;
}
