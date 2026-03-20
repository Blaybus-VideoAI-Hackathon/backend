-- MySQL 기준 영상 길이(duration) 필드 추가

-- scene_videos 테이블에 duration 컬럼 추가
ALTER TABLE scene_videos ADD COLUMN duration INT NULL;

-- 컬럼 추가 확인
DESCRIBE scene_videos;

-- 기존 데이터 확인 (선택사항)
SELECT id, video_url, duration FROM scene_videos LIMIT 5;

-- 인덱스 추가 (성능 향상)
CREATE INDEX idx_scene_videos_duration ON scene_videos(duration);

-- 제약 조건 추가 (선택사항)
-- ALTER TABLE scene_videos ADD CONSTRAINT chk_duration CHECK (duration >= 3 AND duration <= 5);
