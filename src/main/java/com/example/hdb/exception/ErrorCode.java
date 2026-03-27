package com.example.hdb.exception;

public enum ErrorCode {
    // User 관련
    USER_NOT_FOUND("USER_001", "사용자를 찾을 수 없습니다."),
    INVALID_PASSWORD("USER_002", "비밀번호가 일치하지 않습니다."),

    // Project 관련
    PROJECT_NOT_FOUND("PROJECT_001", "프로젝트를 찾을 수 없습니다."),
    SCENE_ORDER_DUPLICATE("PROJECT_002", "씬 순서가 중복됩니다."),

    // Plan 관련
    PLAN_NOT_FOUND("PLAN_001", "기획을 찾을 수 없습니다."),
    PLAN_DATA_CORRUPTED("PLAN_002", "기획 데이터가 손상되었습니다."),

    // Scene 관련
    SCENE_NOT_FOUND("SCENE_001", "씬을 찾을 수 없습니다."),
    SCENE_IMAGE_PROMPT_NOT_FOUND("SCENE_002", "씬의 이미지 프롬프트를 찾을 수 없습니다."),
    SCENE_VIDEO_PROMPT_NOT_FOUND("SCENE_003", "씬의 영상 프롬프트를 찾을 수 없습니다."),
    SCENE_DELETION_FAILED("SCENE_004", "씬 삭제에 실패했습니다."),
    IMAGE_NOT_FOUND("SCENE_005", "이미지를 찾을 수 없습니다."),
    SCENE_IMAGE_NOT_FOUND("SCENE_006", "씬 이미지를 찾을 수 없습니다."),

    // 영상 관련
    VIDEO_MERGE_FAILED("VIDEO_001", "영상 병합에 실패했습니다."),

    // LLM 관련
    LLM_SERVICE_ERROR("LLM_001", "LLM 서비스 오류가 발생했습니다."),
    LLM_GENERATION_FAILED("LLM_002", "LLM 생성에 실패했습니다."),

    // 권한 관련
    UNAUTHORIZED_ACCESS("AUTH_001", "접근 권한이 없습니다."),

    // 공통
    VALIDATION_ERROR("COMMON_001", "입력값이 유효하지 않습니다."),
    INVALID_INPUT_VALUE("COMMON_003", "입력값이 잘못되었습니다."),
    IMAGE_GENERATION_FAILED("COMMON_004", "이미지 생성에 실패했습니다."),
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