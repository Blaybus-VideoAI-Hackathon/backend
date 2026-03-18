# IntelliJ Run Configuration 설정 방법

## 1. Run/Debug Configurations 열기
- Run → Edit Configurations
- Application → HdbApplication 선택

## 2. Environment Variables 추가
- Environment variables 필드에 아래 내용 추가:
```
SPRING_PROFILES_ACTIVE=render;SPRING_DATASOURCE_URL=jdbc:mysql://mysql-0317-hanyu0235-e94f.a.aivencloud.com:18251/backdb?sslMode=REQUIRED&serverTimezone=Asia/Seoul&characterEncoding=UTF-8;SPRING_DATASOURCE_USERNAME=${DB_USERNAME};SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD};OPENAI_API_KEY=${OPENAI_API_KEY}
```

## 3. VM options 확인
- VM options에 이미 `-Dspring.profiles.active=render`가 있음
- Environment variables와 중복되지 않도록 하나만 사용

## 4. Alternative: application-render-local.yml 생성
로컬 테스트용으로 별도 파일을 만들어 하드코딩된 값 사용
