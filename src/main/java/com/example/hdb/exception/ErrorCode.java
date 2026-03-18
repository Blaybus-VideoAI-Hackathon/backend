package com.example.hdb.exception;

public enum ErrorCode {
    // User 관련
    USER_NOT_FOUND("USER_001", "사용자를 찾을 수 없습니다."),
    INVALID_PASSWORD("USER_002", "비밀번호가 일치하지 않습니다."),
    
    // Project 관련
    PROJECT_NOT_FOUND("PROJECT_001", "프로젝트를 찾을 수 없습니다."),
    SCENE_ORDER_DUPLICATE("PROJECT_002", "씬 순서가 중복됩니다."),
    
    // Scene 관련
    SCENE_NOT_FOUND("SCENE_001", "씬을 찾을 수 없습니다."),
    
    // LLM 관련
    LLM_SERVICE_ERROR("LLM_001", "LLM 서비스 오류가 발생했습니다."),
    LLM_GENERATION_FAILED("LLM_002", "LLM 생성에 실패했습니다."),
    
    // 권한 관련
    UNAUTHORIZED_ACCESS("AUTH_001", "접근 권한이 없습니다."),
    
    // 공통
    VALIDATION_ERROR("COMMON_001", "입력값이 유효하지 않습니다."),
    INTERNAL_SERVER_ERROR("COMMON_002", "서버 내부 오류가 발생했습니다.");
    
    private final String code;
    private final String message;
    
    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getMessage() {
        return message;
    }
}
