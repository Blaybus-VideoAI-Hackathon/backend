-- MySQL 기준 이미지 편집 기능을 위한 컬럼 추가

-- Scene 테이블에 edited_image_url 컬럼 추가
ALTER TABLE scenes ADD COLUMN edited_image_url VARCHAR(500) NULL;

-- SceneImage 테이블에 edited_image_url 컬럼 추가  
ALTER TABLE scene_images ADD COLUMN edited_image_url VARCHAR(500) NULL;

-- 컬럼 추가 확인
DESCRIBE scenes;
DESCRIBE scene_images;

-- 기존 데이터 확인 (선택사항)
SELECT id, image_url, edited_image_url FROM scenes LIMIT 5;
SELECT id, image_url, edited_image_url FROM scene_images LIMIT 5;
