package com.example.hdb.dto.kling;

import lombok.Data;

@Data
public class KlingVideoRequest {
    
    private String prompt;
    private String mode = "std";
    private Integer duration = 5;
    private String aspectRatio = "16:9";
    private Boolean inputVideoCrop = false;
    private String cameraMovement = "static";
    private Integer negativePromptStrength = 0;
    
    // 필요시 추가 필드
    private String webhookUrl;
    private String webhookSecret;
}
