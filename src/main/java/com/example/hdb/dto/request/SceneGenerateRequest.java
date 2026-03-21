package com.example.hdb.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Schema(description = "씬 자동 생성 요청")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SceneGenerateRequest {
    
    @Schema(description = "프로젝트 기획을 기반으로 생성할 때 추가 요청사항 (선택사항)", 
           example = "카페 분위기를 강조하고 실제 촬영 가능한 구도로 구성해주세요",
           required = false)
    private String sceneGenerationRequest;
}
