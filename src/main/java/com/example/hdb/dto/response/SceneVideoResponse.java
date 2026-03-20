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
    private String videoUrl;
    private String videoPrompt;
    private Integer duration;
    private String status;
    private String statusDescription;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
