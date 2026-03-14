# LLM API 기술 명세서

## 1. LLM 구조

LLM은 워크플로우형 API 구조로 사용하며, 단계별 역할 분리를 통해 영상 제작 과정을 지원한다. 상태는 백엔드가 관리하며, 모든 응답은 JSON 구조로 반환한다.

## 2. 데이터 구조

### Core Elements
프로젝트 전체에서 유지되는 값이며, AI가 임의로 변경할 수 없다.

```json
{
  "purpose": "",
  "duration": "",
  "ratio": "",
  "style": "",
  "main_character": "",
  "sub_characters": [],
  "background_world": "",
  "story_flow": ""
}
```

### Optional Elements
Scene마다 달라질 수 있는 연출 요소이다.

```json
{
  "action": "",
  "pose": "",
  "camera": "",
  "camera_motion": "",
  "lighting": "",
  "mood": "",
  "time_of_day": "",
  "effects": [],
  "background_characters": "",
  "environment_detail": ""
}
```

### Scene 구조
모든 Scene은 반드시 summary를 가진다.

```json
{
  "scene_id": "",
  "order": 1,
  "summary": "",
  "optional_elements": {},
  "image_prompt": "",
  "video_prompt": ""
}
```

## 3. API 명세

### POST /llm/plan
사용자 아이디어를 분석하고 core_elements를 정리하며, story option 3개를 생성한다.

**Request:**
```json
{
  "project": {
    "title": "",
    "purpose": "",
    "duration": "",
    "ratio": "",
    "style": ""
  },
  "idea_text": ""
}
```

**Response:**
```json
{
  "display_text": "",
  "core_elements": {},
  "meta": {
    "story_options": [
      {
        "id": 1,
        "title": "",
        "description": ""
      }
    ]
  }
}
```

### POST /llm/scenes
선택된 기획안을 기반으로 Scene 리스트를 생성한다.

**Request:**
```json
{
  "core_elements": {},
  "selected_story_option": {}
}
```

**Response:**
```json
{
  "display_text": "",
  "scenes": [
    {
      "order": 1,
      "summary": ""
    }
  ]
}
```

**Scene 생성 규칙:**
- 5초 → 3 scenes
- 10초 → 5 scenes  
- 15초 → 7 scenes

### POST /llm/scene/design
특정 Scene에 대해 optional_elements를 추천하고 image_prompt/video_prompt를 생성한다.

**Request:**
```json
{
  "core_elements": {},
  "scene": {
    "order": 1,
    "summary": ""
  }
}
```

**Response:**
```json
{
  "display_text": "",
  "optional_elements": {},
  "image_prompt": "",
  "video_prompt": "",
  "meta": {
    "quick_actions": []
  }
}
```

### POST /llm/scene/edit
사용자의 수정 요청을 반영하여 scene 정보를 갱신한다.

**Request:**
```json
{
  "core_elements": {},
  "scene": {
    "summary": "",
    "optional_elements": {},
    "image_prompt": "",
    "video_prompt": ""
  },
  "user_edit_text": ""
}
```

**Response:**
```json
{
  "display_text": "",
  "optional_elements": {},
  "image_prompt": "",
  "video_prompt": ""
}
```

## 4. Quick Actions

- 더 강한 액션
- 더 어두운 분위기
- 더 극적인 조명
- 카메라 더 멀리
- 카메라 더 가깝게
- 배경 인물 추가
- 효과 강화

## 5. Backend 처리 규칙

1. LLM은 상태를 기억하지 않으므로, 상태는 백엔드가 관리한다.
2. Project는 core_elements를 저장해야 한다.
3. Scene은 summary, optional_elements, image_prompt, video_prompt, image_url, video_url를 저장해야 한다.
4. LLM 호출 시 항상 필요한 상태를 백엔드가 조합해서 전달해야 한다.
5. core_elements 수정 요청이 명시적으로 들어오면:
   - core_elements 업데이트
   - 기존 scenes 초기화
   - scenes 재생성
6. core_elements는 AI가 임의로 변경하지 않는다.
7. optional_elements만 Scene 단위로 수정 가능하다.
8. 사용자가 core_elements 수정을 명시적으로 요청한 경우에만 core_elements를 변경할 수 있으며, 이 경우 기존 scenes는 초기화 후 재생성한다.

## 6. Prompt 생성 규칙

image_prompt/video_prompt는 core_elements + optional_elements 기반으로 생성한다.

## 7. MVP 구현 범위

- 실제 LLM 연동 전 MockLlmServiceImpl 구현
- H2 Database 사용
- Swagger/OpenAPI 문서화
- Jakarta Validation 적용
- 중앙화된 예외 처리
