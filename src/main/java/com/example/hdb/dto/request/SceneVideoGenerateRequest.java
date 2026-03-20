package com.example.hdb.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "씬 영상 생성 요청")
public class SceneVideoGenerateRequest {
    
    @Schema(
        description = "영상 길이(초)",
        allowableValues = {"3", "4", "5"},
        example = "3",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    @Min(value = 3, message = "영상 길이는 최소 3초입니다.")
    @Max(value = 5, message = "영상 길이는 최대 5초입니다.")
    private Integer duration;
    
    @Schema(
        description = "영상 길이 상세 설명",
        example = "3: 3초, 4: 4초, 5: 5초"
    )
    public static final String DURATION_DESCRIPTION = "3: 3초, 4: 4초, 5: 5초";
}
