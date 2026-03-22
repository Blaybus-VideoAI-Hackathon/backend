package com.example.hdb.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Schema(description = "씬 자동 생성 요청")
@Data
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SceneGenerateRequest {
    
    @Schema(description = "선택된 기획안 ID (1/2/3)", 
           example = "2",
           required = true)
    private Integer selectedPlanId;
}
