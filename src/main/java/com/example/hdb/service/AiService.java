package com.example.hdb.service;

import java.util.List;
import java.util.Map;

public interface AiService {
    List<Map<String, Object>> generatePlanningIdeas(String prompt);
    List<Map<String, Object>> generateScenes(String planningIdea);
    String generateImage(String prompt);
    String generateVideo(String prompt);
}
