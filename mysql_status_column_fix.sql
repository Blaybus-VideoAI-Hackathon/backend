-- MySQL 기준 SceneVideo/SceneImage Status 컬럼 수정 스크립트

-- 1. 현재 컬럼 구조 확인
SHOW COLUMNS FROM scene_videos WHERE Field = 'status';
SHOW COLUMNS FROM scene_images WHERE Field = 'status';

-- 2. 전체 테이블 구조 확인 (선택사항)
DESCRIBE scene_videos;
DESCRIBE scene_images;

-- 3. Status 컬럼 길이 수정
ALTER TABLE scene_videos MODIFY COLUMN status VARCHAR(50) NOT NULL;
ALTER TABLE scene_images MODIFY COLUMN status VARCHAR(50) NOT NULL;

-- 4. 수정된 구조 확인
SHOW COLUMNS FROM scene_videos WHERE Field = 'status';
SHOW COLUMNS FROM scene_images WHERE Field = 'status';

-- 5. 테스트 데이터 삽입 (선택사항)
-- INSERT INTO scene_videos (scene_id, video_url, video_prompt, status, created_at, updated_at) 
-- VALUES (1, NULL, 'test prompt', 'GENERATING', NOW(), NOW());

-- 6. 데이터 확인 (선택사항)
-- SELECT id, status FROM scene_videos WHERE status = 'GENERATING' LIMIT 5;
