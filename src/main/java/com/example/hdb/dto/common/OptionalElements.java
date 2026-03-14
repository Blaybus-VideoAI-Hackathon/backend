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
    private List<String> effects;
    private String backgroundCharacters;
    private String environmentDetail;
}
