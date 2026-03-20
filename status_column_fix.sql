-- SceneVideo status 컬럼 길이 수정
ALTER TABLE scene_videos MODIFY COLUMN status VARCHAR(50) NOT NULL;

-- SceneImage status 컬럼 길이 수정  
ALTER TABLE scene_images MODIFY COLUMN status VARCHAR(50) NOT NULL;

-- 수정된 구조 확인
SHOW CREATE TABLE scene_videos;
SHOW CREATE TABLE scene_images;

-- 테스트 데이터 삽입 (선택사항)
INSERT INTO scene_videos (scene_id, video_url, video_prompt, status, created_at, updated_at) 
VALUES (1, NULL, 'test prompt', 'GENERATING', NOW(), NOW());

-- 데이터 확인
SELECT * FROM scene_videos WHERE status = 'GENERATING';
