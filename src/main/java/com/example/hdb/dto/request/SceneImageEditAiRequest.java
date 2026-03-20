package com.example.hdb.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "씬 이미지 AI 편집 요청")
public class SceneImageEditAiRequest {
    
    @Schema(
        description = "사용자 편집 요청 텍스트",
        example = "밝기를 조금 높이고 더 따뜻한 느낌으로 바꿔줘",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "편집 요청 텍스트는 필수입니다.")
    private String userEditText;
    
    // 직접 getter 추가 (Lombok 보험용)
    public String getUserEditText() {
        return userEditText;
    }
}
