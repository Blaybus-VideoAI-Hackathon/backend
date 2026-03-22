package com.example.hdb.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

@Schema(description = "선택된 기획안 분석 응답")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanAnalysisResponse {
    
    @Schema(description = "프로젝트 ID")
    private Long projectId;
    
    @Schema(description = "선택된 기획안 ID")
    private Integer selectedPlanId;
    
    @Schema(description = "프로젝트 핵심요소")
    private ProjectCore projectCore;
    
    @Schema(description = "씬 플랜")
    private ScenePlan scenePlan;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProjectCore {
        
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
    public static class ScenePlan {
        
        @Schema(description = "추천 씬 개수")
        private Integer recommendedSceneCount;
        
        @Schema(description = "씬 목록")
        private List<SceneInfo> scenes;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SceneInfo {
        
        @Schema(description = "씬 순서")
        private Integer sceneOrder;
        
        @Schema(description = "씬 요약")
        private String summary;
        
        @Schema(description = "씬 목표")
        private String sceneGoal;
        
        @Schema(description = "감성 비트")
        private String emotionBeat;
        
        @Schema(description = "예상 소요 시간(초)")
        private Integer estimatedDuration;
    }
}
