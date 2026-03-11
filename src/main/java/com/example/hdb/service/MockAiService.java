package com.example.hdb.service;

import java.util.List;
import java.util.Map;

public interface MockAiService {
    List<Map<String, Object>> generatePlan();
    List<Map<String, Object>> generateScenes();
    String generateImage();
    String generateVideo();
}
