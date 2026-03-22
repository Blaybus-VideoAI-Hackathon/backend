package com.example.hdb.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Schema(description = "기획안 선택 요청")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanSelectRequest {
    
    @Schema(description = "선택된 기획안 ID (1/2/3)", 
           example = "2",
           required = true)
    private Integer selectedPlanId;
}
