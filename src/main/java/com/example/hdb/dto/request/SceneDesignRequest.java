package com.example.hdb.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Schema(description = "씬 설계 요청")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SceneDesignRequest {
    
    @Schema(description = "사용자 설계 요청 (선택사항)")
    private String designRequest;
}
