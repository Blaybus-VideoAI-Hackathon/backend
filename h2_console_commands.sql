-- SceneImage 테이블 구조 확인
SHOW CREATE TABLE scene_images;

-- SceneVideo 테이블 구조 확인  
SHOW CREATE TABLE scene_videos;

-- image_url 컬럼 nullable로 수정
ALTER TABLE scene_images MODIFY COLUMN image_url VARCHAR(500) NULL;

-- video_url 컬럼 nullable로 수정
ALTER TABLE scene_videos MODIFY COLUMN video_url VARCHAR(500) NULL;

-- 수정된 구조 확인
SHOW CREATE TABLE scene_images;
SHOW CREATE TABLE scene_videos;
