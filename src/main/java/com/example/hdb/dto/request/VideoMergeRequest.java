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
@Schema(description = "영상 병합 요청")
public class VideoMergeRequest {
    @Schema(
        description = "영상 없는 Scene 건너뛸지 여부",
        example = "false",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    private Boolean skipMissingVideos;  // 영상 없는 Scene 건너뛸지 여부 (기본: false)
    
    @Schema(
        description = "출력 포맷",
        allowableValues = {"mp4", "avi", "mov"},
        example = "mp4",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    private String outputFormat;        // 출력 포맷 (기본: mp4)
    
    @Schema(
        description = "출력 품질",
        allowableValues = {"480", "720", "1080"},
        example = "720",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    private Integer outputQuality;       // 출력 품질 (기본: 720)
    
    // 직접 getter 추가 (Lombok 보험용)
    public Boolean getSkipMissingVideos() {
        return skipMissingVideos;
    }
}
