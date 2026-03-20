package com.example.hdb.dto.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    
    @JsonProperty("max_tokens")
    private int max_tokens;
    
    // 직접 getter/setter 추가 (Lombok 보험용)
    public String getModel() {
        return model;
    }
    
    public void setModel(String model) {
        this.model = model;
    }
    
    public List<Map<String, String>> getMessages() {
        return messages;
    }
    
    public void setMessages(List<Map<String, String>> messages) {
        this.messages = messages;
    }
    
    public double getTemperature() {
        return temperature;
    }
    
    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }
    
    public int getMax_tokens() {
        return max_tokens;
    }
    
    public void setMax_tokens(int max_tokens) {
        this.max_tokens = max_tokens;
    }
}
