package com.example.hdb.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Schema(description = "씬 생성 요청")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SceneGenerateRequest {
    
    @Schema(description = "씬 생성 요청")
    private String sceneGenerationRequest;
}
