package com.example.hdb.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "씬 설계 재추천 요청")
public class SceneDesignRegenerateRequest {
    
    @Schema(
        description = "재추천 방식",
        allowableValues = {"variation", "complete"},
        example = "variation",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    private String regenerateType;
    
    @Schema(
        description = "사용자 추가 요청사항",
        example = "더 밝은 분위기로 바꿔주세요",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    private String userRequest;
    
    // 직접 getter 추가 (Lombok 보험용)
    public String getUserRequest() {
        return userRequest;
    }
}
