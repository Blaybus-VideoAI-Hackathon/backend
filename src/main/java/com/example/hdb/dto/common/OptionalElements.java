package com.example.hdb.dto.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptionalElements {
    private String action;
    private String pose;
    private String camera;
    private String cameraMotion;
    private String lighting;
    private String mood;
    private String timeOfDay;
    private List<String> effects;       // JSON 배열로 저장 (LLM이 String으로 줘도 파싱 안전하게 처리)
    private String backgroundCharacters;
    private String environmentDetail;
}