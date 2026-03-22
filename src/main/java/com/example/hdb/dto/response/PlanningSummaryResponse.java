package com.example.hdb.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Schema(description = "프로젝트 기획 요약 응답")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanningSummaryResponse {
    
    @Schema(description = "프로젝트 ID")
    private Long projectId;
    
    @Schema(description = "선택된 기획안 ID")
    private Integer selectedPlanId;
    
    @Schema(description = "선택된 기획안 제목")
    private String selectedPlanTitle;
    
    @Schema(description = "영상 목적")
    private String purpose;
    
    @Schema(description = "영상 길이")
    private Integer duration;
    
    @Schema(description = "영상 비율")
    private String ratio;
    
    @Schema(description = "영상 스타일")
    private String style;
    
    @Schema(description = "주요 캐릭터")
    private String mainCharacter;
    
    @Schema(description = "보조 캐릭터")
    private java.util.List<String> subCharacters;
    
    @Schema(description = "배경 세계관")
    private String backgroundWorld;
    
    @Schema(description = "전체 스토리 흐름")
    private String storyFlow;
    
    @Schema(description = "스토리라인")
    private String storyLine;
}
