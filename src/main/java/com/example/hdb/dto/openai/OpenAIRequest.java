package com.example.hdb.dto.openai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OpenAIRequest {
    private String model;
    private List<Map<String, String>> messages;
    private double temperature;
    private int max_tokens;
}
