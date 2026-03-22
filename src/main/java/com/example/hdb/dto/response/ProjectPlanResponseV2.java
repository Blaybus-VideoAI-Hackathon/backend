package com.example.hdb.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Schema(description = "프로젝트 기획안 응답 V2 (plans 배열 구조)")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectPlanResponseV2 {
    
    @Schema(description = "기획안 목록")
    private List<Plan> plans;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Plan {
        
        @Schema(description = "기획안 ID (1/2/3)")
        private Integer planId;
        
        @Schema(description = "기획안 제목")
        private String title;
        
        @Schema(description = "기획안 초점")
        private String focus;
        
        @Schema(description = "기획안 설명")
        private String displayText;
        
        @Schema(description = "핵심 요소")
        private CoreElements coreElements;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CoreElements {
        
        @Schema(description = "주요 캐릭터")
        private String mainCharacter;
        
        @Schema(description = "배경 설정")
        private String background;
        
        @Schema(description = "스타일")
        private String style;
        
        @Schema(description = "화면 비율")
        private String ratio;
        
        @Schema(description = "목적")
        private String purpose;
    }
    
    public static ProjectPlanResponseV2 fromJson(String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return objectMapper.readValue(json, ProjectPlanResponseV2.class);
        } catch (Exception e) {
            System.err.println("Failed to parse ProjectPlanResponseV2 JSON: " + json + ", error: " + e.getMessage());
            return createFallbackResponse();
        }
    }
    
    public static ProjectPlanResponseV2 createFallbackResponse() {
        List<Plan> plans = java.util.List.of(
            new Plan(1, "제품 중심 기획안", "제품 중심", 
                "제품의 혁신적인 디자인과 기술적 우수성을 강조하는 세련된 테크 기획안입니다.",
                new CoreElements()),
            new Plan(2, "감성 중심 기획안", "감성 중심", 
                "사용자의 감성적 경험과 라이프스타일 변화를 그리는 감동적인 스토리텔링 기획안입니다.",
                new CoreElements()),
            new Plan(3, "기능 시연 중심 기획안", "기능 시연 중심", 
                "실제 사용 방법과 다양한 활용 장면을 구체적으로 보여주는 실용적인 기획안입니다.",
                new CoreElements())
        );
        
        // CoreElements 기본값 설정
        for (Plan plan : plans) {
            plan.getCoreElements().setMainCharacter("주요 인물");
            plan.getCoreElements().setBackground("배경 설정");
            plan.getCoreElements().setStyle("스타일");
            plan.getCoreElements().setRatio("16:9");
            plan.getCoreElements().setPurpose("프로모션");
        }
        
        return new ProjectPlanResponseV2(plans);
    }
}
