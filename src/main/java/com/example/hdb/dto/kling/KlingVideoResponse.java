package com.example.hdb.dto.kling;

import lombok.Data;

@Data
public class KlingVideoResponse {
    
    private String taskId;
    private String status; // pending, processing, success, failed
    private String videoUrl;
    private String thumbnailUrl;
    private String errorMessage;
    private Integer progress;
    private Long createdAt;
    private Long completedAt;
    
    // 필요시 추가 필드
    private String code;
    private String message;
}
