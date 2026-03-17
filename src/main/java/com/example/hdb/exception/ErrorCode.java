package com.example.hdb.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    
    PROJECT_NOT_FOUND("PROJECT_001", "Project not found"),
    
    SCENE_NOT_FOUND("SCENE_001", "Scene not found"),
    INVALID_SCENE_ORDER("SCENE_002", "Invalid scene order"),
    INVALID_SCENE_STATUS("SCENE_003", "Invalid scene status"),
    
    USER_NOT_FOUND("USER_001", "User not found"),
    INVALID_PASSWORD("USER_002", "Invalid password"),
    
    LLM_SERVICE_ERROR("LLM_001", "LLM service error"),
    LLM_GENERATION_FAILED("LLM_002", "LLM generation failed"),
    INVALID_CORE_ELEMENTS("LLM_003", "Invalid core elements"),
    INVALID_SCENE_REQUEST("LLM_004", "Invalid scene request"),
    
    VALIDATION_ERROR("VALIDATION_001", "Validation error"),
    INTERNAL_SERVER_ERROR("SERVER_001", "Internal server error");
    
    private final String code;
    private final String message;
}
