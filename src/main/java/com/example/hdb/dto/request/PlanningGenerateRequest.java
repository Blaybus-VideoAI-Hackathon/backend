package com.example.hdb.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Schema(description = "프로젝트 기획 생성 요청")
@Data
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanningGenerateRequest {
    
    @Schema(description = "사용자 기획 프롬프트", 
           example = "작은 햄스터가 여러 옷을 갈아입으며 귀엽게 변신하는 따뜻한 숏폼 광고로 만들고 싶어",
           required = true)
    private String userPrompt;
}
