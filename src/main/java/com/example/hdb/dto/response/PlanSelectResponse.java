package com.example.hdb.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Schema(description = "기획안 선택 응답")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanSelectResponse {
    
    @Schema(description = "선택된 기획안 ID")
    private Integer selectedPlanId;
    
    @Schema(description = "선택된 기획안 제목")
    private String selectedTitle;
    
    @Schema(description = "선택된 기획안 초점")
    private String selectedFocus;
}
