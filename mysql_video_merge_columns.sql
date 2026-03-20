-- MySQL 기준 영상 병합 기능을 위한 컬럼 추가

-- projects 테이블에 final_video_url 컬럼 추가
ALTER TABLE projects ADD COLUMN final_video_url VARCHAR(500) NULL;

-- 컬럼 추가 확인
DESCRIBE projects;

-- 기존 데이터 확인 (선택사항)
SELECT id, title, final_video_url FROM projects LIMIT 5;

-- 인덱스 추가 (성능 향상)
CREATE INDEX idx_projects_final_video_url ON projects(final_video_url);
