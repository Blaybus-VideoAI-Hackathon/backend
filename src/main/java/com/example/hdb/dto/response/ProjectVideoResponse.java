package com.example.hdb.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectVideoResponse {
    private Long projectId;
    private String finalVideoUrl;
    private String status;  // READY, NOT_CREATED, PROCESSING
    private String message;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // 기본 생성자 추가
    public ProjectVideoResponse(Long projectId, String finalVideoUrl, String status, String message) {
        this.projectId = projectId;
        this.finalVideoUrl = finalVideoUrl;
        this.status = status;
        this.message = message;
    }
}
