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
    
    VALIDATION_ERROR("VALIDATION_001", "Validation error"),
    INTERNAL_SERVER_ERROR("SERVER_001", "Internal server error");
    
    private final String code;
    private final String message;
}
