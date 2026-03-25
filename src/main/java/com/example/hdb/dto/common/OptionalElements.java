package com.example.hdb.dto.common;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptionalElements {
    private String action;
    private String pose;
    private String camera;
    private String cameraMotion;
    private String lighting;
    private String mood;
    private String timeOfDay;

    @JsonDeserialize(using = EffectsDeserializer.class)
    private List<String> effects;

    private String backgroundCharacters;
    private String environmentDetail;

    // LLM이 String 또는 배열로 줄 수 있어서 둘 다 처리
    public static class EffectsDeserializer extends JsonDeserializer<List<String>> {
        @Override
        public List<String> deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            List<String> result = new ArrayList<>();

            if (node.isArray()) {
                for (JsonNode item : node) {
                    if (!item.asText().isBlank()) {
                        result.add(item.asText());
                    }
                }
            } else if (node.isTextual()) {
                // "soft glow, warm bokeh" 같은 문자열을 콤마로 분리
                String text = node.asText().trim();
                if (!text.isBlank()) {
                    for (String part : text.split(",")) {
                        String trimmed = part.trim();
                        if (!trimmed.isBlank()) {
                            result.add(trimmed);
                        }
                    }
                }
            }

            return result;
        }
    }
}