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
@Schema(description = "이미지 편집 완료 요청")
public class ImageEditCompleteRequest {

    @Schema(description = "편집된 이미지 Base64 데이터", required = false)
    private String editedImageBase64;

    @Schema(description = "편집 노트/메모", required = false)
    private String editNotes;
}