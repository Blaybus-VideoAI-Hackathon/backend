package com.example.hdb.service.impl;

import com.example.hdb.dto.common.*;
import com.example.hdb.dto.request.*;
import com.example.hdb.dto.response.*;
import com.example.hdb.enums.QuickActionType;
import com.example.hdb.service.LlmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Service
@Profile("no-db")
@Slf4j
public class LlmServiceImplNoDb implements LlmService {

    private final Random random = new Random();

    @Override
    public LlmPlanResponse generatePlan(LlmPlanRequest request) {
        log.info("No-DB LLM: Generating plan for project: {}", request.getProject().getTitle());

        CoreElements coreElements = CoreElements.builder()
                .purpose(request.getProject().getPurpose() != null ? request.getProject().getPurpose() : "영상 제작")
                .duration(request.getProject().getDuration() != null ? request.getProject().getDuration() : "10초")
                .ratio(request.getProject().getRatio() != null ? request.getProject().getRatio() : "16:9")
                .style(request.getProject().getStyle() != null ? request.getProject().getStyle() : "현대적인 스타일")
                .mainCharacter("주인공")
                .subCharacters(Arrays.asList("조연1", "조연2"))
                .backgroundWorld("현대 도시")
                .storyFlow("시작-전개-절정-결말")
                .build();

        List<StoryOption> storyOptions = Arrays.asList(
                StoryOption.builder()
                        .id(1)
                        .title("일상적인 이야기")
                        .description("주인공의 일상을 따뜻하게 그리는 이야기")
                        .build(),
                StoryOption.builder()
                        .id(2)
                        .title("모험 이야기")
                        .description("주인공이 새로운 세계를 탐험하는 이야기")
                        .build(),
                StoryOption.builder()
                        .id(3)
                        .title("성장 이야기")
                        .description("주인공이 성장하며 변화하는 이야기")
                        .build()
        );

        return LlmPlanResponse.builder()
                .displayText("No-DB: 프로젝트 기획안이 성공적으로 생성되었습니다. 3가지 스토리 옵션 중 하나를 선택해주세요.")
                .coreElements(coreElements)
                .meta(LlmPlanResponse.Meta.builder()
                        .storyOptions(storyOptions)
                        .build())
                .build();
    }

    @Override
    public LlmScenesResponse generateScenes(LlmScenesRequest request) {
        log.info("No-DB LLM: Generating scenes for duration: {}", request.getCoreElements().getDuration());

        int sceneCount = calculateSceneCount(request.getCoreElements().getDuration());

        List<SceneInfo> scenes = Arrays.asList(
                SceneInfo.builder()
                        .sceneId(UUID.randomUUID().toString())
                        .order(1)
                        .summary("No-DB 오프닝 - 주인공 소개")
                        .build(),
                SceneInfo.builder()
                        .sceneId(UUID.randomUUID().toString())
                        .order(2)
                        .summary("No-DB 전개 - 문제 제기")
                        .build(),
                SceneInfo.builder()
                        .sceneId(UUID.randomUUID().toString())
                        .order(3)
                        .summary("No-DB 절정 - 갈등 해결")
                        .build(),
                SceneInfo.builder()
                        .sceneId(UUID.randomUUID().toString())
                        .order(4)
                        .summary("No-DB 결말 - 마무리")
                        .build(),
                SceneInfo.builder()
                        .sceneId(UUID.randomUUID().toString())
                        .order(5)
                        .summary("No-DB 엔딩 - 여운")
                        .build()
        ).subList(0, sceneCount);

        return LlmScenesResponse.builder()
                .displayText(String.format("No-DB: %d개의 Scene이 성공적으로 생성되었습니다.", sceneCount))
                .scenes(scenes)
                .build();
    }

    @Override
    public LlmSceneDesignResponse designScene(LlmSceneDesignRequest request) {
        log.info("No-DB LLM: Designing scene: {}", request.getScene().getSummary());

        OptionalElements optionalElements = OptionalElements.builder()
                .action("No-DB 자연스러운 움직임")
                .pose("No-DB 정면을 바라보는 자세")
                .camera("No-DB 중정면 샷")
                .cameraMotion("No-DB 천천히 줌인")
                .lighting("No-DB 부드러운 자연광")
                .mood("No-DB 차분한 분위기")
                .timeOfDay("낮")
                .effects(Arrays.asList("No-DB 부드러운 전환 효과"))
                .backgroundCharacters("No-DB 주변 사람들")
                .environmentDetail("No-DB 현대적인 실내")
                .build();

        List<QuickAction> quickActions = Arrays.asList(
                QuickAction.builder()
                        .id("stronger_action")
                        .label(QuickActionType.STRONGER_ACTION.getLabel())
                        .action("더 강한 액션")
                        .build(),
                QuickAction.builder()
                        .id("darker_mood")
                        .label(QuickActionType.DARKER_MOOD.getLabel())
                        .action("더 어두운 분위기")
                        .build(),
                QuickAction.builder()
                        .id("dramatic_lighting")
                        .label(QuickActionType.DRAMATIC_LIGHTING.getLabel())
                        .action("더 극적인 조명")
                        .build()
        );

        return LlmSceneDesignResponse.builder()
                .displayText("No-DB: Scene 디자인이 완료되었습니다. 퀵 액션으로 쉽게 수정할 수 있습니다.")
                .optionalElements(optionalElements)
                .imagePrompt("No-DB Professional portrait of main character in modern setting, soft lighting, natural pose")
                .videoPrompt("No-DB Medium shot of character walking in modern urban environment, smooth camera movement")
                .meta(LlmSceneDesignResponse.Meta.builder()
                        .quickActions(quickActions)
                        .build())
                .build();
    }

    @Override
    public LlmSceneEditResponse editScene(LlmSceneEditRequest request) {
        log.info("No-DB LLM: Editing scene with request: {}", request.getUserEditText());

        OptionalElements updatedElements = OptionalElements.builder()
                .action("No-DB 더 역동적인 움직임")
                .pose("No-DB 측면을 바라보는 자세")
                .camera("No-DB 측면 샷")
                .cameraMotion("No-DB 빠른 패닝")
                .lighting("No-DB 드라마틱한 조명")
                .mood("No-DB 긴장감 넘치는 분위기")
                .timeOfDay("저녁")
                .effects(Arrays.asList("No-DB 강한 전환 효과", "No-DB 모션 블러"))
                .backgroundCharacters("No-DB 없음")
                .environmentDetail("No-DB 어두운 거리")
                .build();

        return LlmSceneEditResponse.builder()
                .displayText("No-DB: 사용자 요청에 따라 Scene이 성공적으로 수정되었습니다.")
                .optionalElements(updatedElements)
                .imagePrompt("No-DB Dramatic side profile of character in dark street, intense lighting, dynamic pose")
                .videoPrompt("No-DB Fast panning shot of character running through urban night environment, dramatic lighting")
                .build();
    }

    private int calculateSceneCount(String duration) {
        if (duration == null) return 5;
        
        if (duration.contains("5")) return 3;
        if (duration.contains("10")) return 5;
        if (duration.contains("15")) return 7;
        
        return 5; // 기본값
    }
}
