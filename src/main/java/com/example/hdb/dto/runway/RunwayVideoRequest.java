package com.example.hdb.dto.runway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunwayVideoRequest {
    private String promptText;
    private String promptImage;
    private Integer duration;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RunwayRequestBody {
        private String model;
        private String promptText;
        private String promptImage;
        private String ratio;
        private Integer duration;
    }
}
