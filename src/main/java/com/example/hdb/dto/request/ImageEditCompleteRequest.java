package com.example.hdb.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageEditCompleteRequest {
    private String editedImageUrl;  // 편집된 이미지 URL 또는 파일 경로
    private String editType;        // 편집 타입 (brightness, crop, etc.)
    private String editParameters;   // 편집 파라미터 JSON
}
