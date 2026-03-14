package com.example.hdb.dto.response;

import com.example.hdb.dto.common.OptionalElements;
import com.example.hdb.dto.common.QuickAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmSceneDesignResponse {
    private String displayText;
    private OptionalElements optionalElements;
    private String imagePrompt;
    private String videoPrompt;
    private Meta meta;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Meta {
        private List<QuickAction> quickActions;
    }
}
