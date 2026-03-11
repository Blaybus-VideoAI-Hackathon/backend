package com.example.hdb.service.impl;

import com.example.hdb.service.MockAiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class MockAiServiceImpl implements MockAiService {

    private final Random random = new Random();
    
    private final List<String> planTitles = Arrays.asList(
        "여행 브이로그: 제주도 3박 4일",
        "요리 레시피: 한식 마스터 클래스",
        "IT 기기 리뷰: 최신 스마트폰",
        "패션 튜토리얼: 봄 코디 추천",
        "운동 루틴: 홈트레이닝 30일",
        "게임 플레이: 신작 RPG 공략"
    );
    
    private final List<String> styles = Arrays.asList(
        "자연스러운 다큐멘터리",
        "밝고 활기찬 분위기",
        "기술적이고 전문적인",
        "감성적이고 아름다운",
        "역동적이고 에너제틱한",
        "차분하고 안정적인"
    );
    
    private final List<String> ratios = Arrays.asList("16:9", "9:16", "4:3", "1:1");

    @Override
    public List<Map<String, Object>> generatePlan() {
        log.info("Mock AI: Generating 3 random planning ideas");
        
        List<Map<String, Object>> plans = new ArrayList<>();
        
        for (int i = 0; i < 3; i++) {
            Map<String, Object> plan = new HashMap<>();
            plan.put("id", (long) (i + 1));
            plan.put("title", getRandomElement(planTitles));
            plan.put("description", "자동 생성된 기획안 설명 " + (i + 1));
            plan.put("style", getRandomElement(styles));
            plan.put("ratio", getRandomElement(ratios));
            plan.put("scenes", 5);
            plans.add(plan);
        }
        
        return plans;
    }

    @Override
    public List<Map<String, Object>> generateScenes() {
        log.info("Mock AI: Generating 5 random scenes");
        
        List<Map<String, Object>> scenes = new ArrayList<>();
        
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> scene = new HashMap<>();
            scene.put("sceneOrder", i);
            scene.put("title", "Scene " + i + ": " + getRandomSceneTitle());
            scene.put("description", "Scene " + i + "의 상세 설명입니다. 랜덤으로 생성된 내용입니다.");
            scene.put("coreElements", generateRandomJsonArray());
            scene.put("optionalElements", generateRandomJsonArray());
            scene.put("imagePrompt", "Scene " + i + "의 이미지 생성 프롬프트");
            scene.put("videoPrompt", "Scene " + i + "의 영상 생성 프롬프트");
            scenes.add(scene);
        }
        
        return scenes;
    }

    @Override
    public String generateImage() {
        String imageUrl = "https://mock-images.com/generated/" + UUID.randomUUID() + ".jpg";
        log.info("Mock AI: Generated dummy image URL: {}", imageUrl);
        return imageUrl;
    }

    @Override
    public String generateVideo() {
        String videoUrl = "https://mock-videos.com/generated/" + UUID.randomUUID() + ".mp4";
        log.info("Mock AI: Generated dummy video URL: {}", videoUrl);
        return videoUrl;
    }
    
    private <T> T getRandomElement(List<T> list) {
        return list.get(random.nextInt(list.size()));
    }
    
    private String generateRandomJsonArray() {
        List<String> elements = Arrays.asList("요소A", "요소B", "요소C", "요소D", "요소E");
        List<String> selected = new ArrayList<>();
        
        int count = random.nextInt(3) + 2; // 2-4개 요소
        for (int i = 0; i < count; i++) {
            selected.add("\"" + getRandomElement(elements) + "\"");
        }
        
        return "[" + String.join(", ", selected) + "]";
    }
    
    private String getRandomSceneTitle() {
        List<String> titles = Arrays.asList(
            "오프닝", "소개", "본론1", "본론2", "결론",
            "시작", "발전", "절정", "마무리", "엔딩"
        );
        return getRandomElement(titles);
    }
}
