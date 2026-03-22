package com.example.hdb.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

@Schema(description = "프로젝트 기획 생성 응답")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanningGenerateResponse {
    
    @Schema(description = "프로젝트 ID")
    private Long projectId;
    
    @Schema(description = "선택된 기획안 ID")
    private Integer selectedPlanId;
    
    @Schema(description = "기획 요약")
    private PlanningSummary planningSummary;
    
    @Schema(description = "기획안 목록")
    private List<Plan> plans;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlanningSummary {
        
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
        private List<String> subCharacters;
        
        @Schema(description = "배경 세계관")
        private String backgroundWorld;
        
        @Schema(description = "전체 스토리 흐름")
        private String storyFlow;
        
        @Schema(description = "스토리라인")
        private String storyLine;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Plan {
        
        @Schema(description = "기획안 ID")
        private Integer planId;
        
        @Schema(description = "기획안 제목")
        private String title;
        
        @Schema(description = "기획안 초점")
        private String focus;
        
        @Schema(description = "기획안 설명")
        private String displayText;
        
        @Schema(description = "추천 이유")
        private String recommendationReason;
        
        @Schema(description = "강점 목록")
        private List<String> strengths;
        
        @Schema(description = "타겟 분위기")
        private String targetMood;
        
        @Schema(description = "타겟 사용 사례")
        private String targetUseCase;
        
        @Schema(description = "스토리라인")
        private String storyLine;
        
        @Schema(description = "핵심 요소")
        private CoreElements coreElements;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CoreElements {
        
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
        private List<String> subCharacters;
        
        @Schema(description = "배경 세계관")
        private String backgroundWorld;
        
        @Schema(description = "전체 스토리 흐름")
        private String storyFlow;
        
        @Schema(description = "스토리라인")
        private String storyLine;
    }
}
