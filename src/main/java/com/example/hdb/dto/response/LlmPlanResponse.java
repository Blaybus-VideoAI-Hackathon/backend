package com.example.hdb.dto.response;

import com.example.hdb.dto.common.CoreElements;
import com.example.hdb.dto.common.StoryOption;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmPlanResponse {
    private String displayText;
    private CoreElements coreElements;
    private Meta meta;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Meta {
        private List<StoryOption> storyOptions;
    }
}
