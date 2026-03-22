package com.example.hdb.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

@Schema(description = "프로젝트 기획안 응답 V3 (차별화 강화)")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectPlanResponseV3 {
    
    @Schema(description = "기획안 목록")
    private List<Plan> plans;
    
    public static ProjectPlanResponseV3 fromJson(String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return objectMapper.readValue(json, ProjectPlanResponseV3.class);
        } catch (Exception e) {
            System.err.println("Failed to parse ProjectPlanResponseV3 JSON: " + json + ", error: " + e.getMessage());
            return createFallbackResponse();
        }
    }
    
    public static ProjectPlanResponseV3 createFallbackResponse() {
        List<Plan> plans = java.util.List.of(
            new Plan(1, "감성 중심 기획안", "감성 중심", 
                "주인공의 성장과 변화를 따뜻하게 그려내며 시청자의 감성을 자극하는 스토리텔링 광고입니다.",
                "따뜻한 감성으로 브랜드 이미지를 강화하고, 시청자의 공감을 얻어 자발적인 공유를 유도합니다.",
                java.util.List.of("강력한 감성 연결", "높은 공감도", "바이럴 확산 가능성"),
                "따뜻함, 감동, 희망",
                "브랜드 인지도 향상, 감성 마케팅",
                new CoreElements("숏폼형", 20, "9:16", "귀엽고 따뜻한 감성 광고", "성장하는 주인공", 
                    java.util.List.of("조력자", "경쟁자"), "현실적인 일상 공간", "도입 → 갈등 → 성장 → 해결", 
                    "일상의 고민에 부딪힌 주인공이 브랜드 제품을 통해 새로운 가능성을 발견하고 성장하는 이야기"),
                "일상의 고민에 부딪힌 주인공이 브랜드 제품을 통해 새로운 가능성을 발견하고 성장하는 이야기"),
            new Plan(2, "액션 중심 기획안", "액션 중심", 
                "빠른 템포와 역동적인 장면으로 시청자의 시선을 사로잡는 액션 광고입니다.",
                "강렬한 비주얼과 빠른 전개로 짧은 시간 내 최대의 임팩트를 전달합니다.",
                java.util.List.of("강력한 시선 집중", "높은 기억력", "숏폼 최적화"),
                "긴장감, 스릴, 에너지",
                "신제품 하이라이트, 기능 시연",
                new CoreElements("숏폼형", 20, "9:16", "귀엽고 따뜻한 감성 광고", "역동적인 주인공", 
                    java.util.List.of("도전 과제", "목표물"), "현대적인 도시 공간", "훅 → 전개 → 클라이맥스", 
                    "도전 과제를 극복하는 주인공이 브랜드 제품의 도움으로 성공하는 역동적인 이야기"),
                "도전 과제를 극복하는 주인공이 브랜드 제품의 도움으로 성공하는 역동적인 이야기"),
            new Plan(3, "코미디 중심 기획안", "코미디 중심", 
                "유머와 재치로 시청자에게 웃음을 선사하며 브랜드를 친근하게 만드는 코미디 광고입니다.",
                "긍정적인 감정과 즐거움을 통해 브랜드에 대한 호감도를 극대화합니다.",
                java.util.List.of("강력한 감정 연결", "높은 공감도", "바이럴 확산 가능성"),
                "유쾌함, 재미, 친근함",
                "브랜드 친근화, 바이럴 마케팅",
                new CoreElements("숏폼형", 20, "9:16", "귀엽고 따뜻한 감성 광고", "유쾌한 주인공", 
                    java.util.List.of("웃음 유발 캐릭터", "엉뚱한 상황"), "일상적인 공간", "소개 → 갈등 → 유머 해결", 
                    "엉뚱한 상황에 처한 주인공이 브랜드 제품으로 유머러스하게 문제를 해결하는 이야기"),
                "엉뚱한 상황에 처한 주인공이 브랜드 제품으로 유머러스하게 문제를 해결하는 이야기")
        );
        
        return new ProjectPlanResponseV3(plans);
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
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
        
        @Schema(description = "핵심 요소")
        private CoreElements coreElements;
        
        @Schema(description = "스토리라인")
        private String storyLine;
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
