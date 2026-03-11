package com.example.hdb.service.impl;

import com.example.hdb.service.AiService;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AiServiceImpl implements AiService {

    @Override
    public List<Map<String, Object>> generatePlanningIdeas(String prompt) {
        List<Map<String, Object>> ideas = new ArrayList<>();
        
        // 기획안 1: 여행 브이로그
        Map<String, Object> idea1 = new HashMap<>();
        idea1.put("id", 1);
        idea1.put("title", "제주도 3박 4일 여행 브이로그");
        idea1.put("description", "제주도의 아름다운 자연과 맛집을 중심으로 한 여행 브이로그");
        idea1.put("style", "자연스러운 다큐멘터리 스타일");
        idea1.put("ratio", "16:9");
        idea1.put("scenes", 5);
        ideas.add(idea1);
        
        // 기획안 2: 요리 레시피
        Map<String, Object> idea2 = new HashMap<>();
        idea2.put("id", 2);
        idea2.put("title", "한식 요리 마스터 클래스");
        idea2.put("description", "전통 한식 요리법을 현대적으로 재해석한 요리 영상");
        idea2.put("style", "밝고 활기찬 분위기");
        idea2.put("ratio", "9:16");
        idea2.put("scenes", 5);
        ideas.add(idea2);
        
        // 기획안 3: IT 기기 리뷰
        Map<String, Object> idea3 = new HashMap<>();
        idea3.put("id", 3);
        idea3.put("title", "최신 스마트폰 심층 리뷰");
        idea3.put("description", "신규 출시된 스마트폰의 성능과 기능을 상세히 분석");
        idea3.put("style", "기술적이고 전문적인 스타일");
        idea3.put("ratio", "16:9");
        idea3.put("scenes", 5);
        ideas.add(idea3);
        
        return ideas;
    }

    @Override
    public List<Map<String, Object>> generateScenes(String planningIdea) {
        List<Map<String, Object>> scenes = new ArrayList<>();
        
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> scene = new HashMap<>();
            scene.put("sceneOrder", i);
            scene.put("title", "Scene " + i);
            scene.put("description", "This is scene number " + i + " with detailed description");
            scene.put("coreElements", "[\"요소1\", \"요소2\", \"요소3\"]");
            scene.put("optionalElements", "[\"옵션1\", \"옵션2\"]");
            scene.put("imagePrompt", "Professional image for scene " + i);
            scene.put("videoPrompt", "Dynamic video content for scene " + i);
            scenes.add(scene);
        }
        
        return scenes;
    }

    @Override
    public String generateImage(String prompt) {
        return "https://mock-images.com/generated/" + UUID.randomUUID().toString() + ".jpg";
    }

    @Override
    public String generateVideo(String prompt) {
        return "https://mock-videos.com/generated/" + UUID.randomUUID().toString() + ".mp4";
    }
}
