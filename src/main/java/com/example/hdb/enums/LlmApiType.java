package com.example.hdb.enums;

public enum LlmApiType {
    PLAN("기획안 생성"),
    SCENES("씬 목록 생성"),
    SCENE_DESIGN("씬 디자인"),
    SCENE_EDIT("씬 수정");
    
    private final String description;
    
    LlmApiType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
