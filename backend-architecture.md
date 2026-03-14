HDB Backend Architecture
1. Overview

HDB 백엔드는 Spring Boot 기반 REST API 서버로 구성되어 있으며 프로젝트(Project)와 장면(Scene)을 중심으로 AI 영상 생성 워크플로우를 처리한다.

현재 MVP 단계에서는 실제 AI 모델 대신 MockAiServiceImpl을 사용하여 기획안, Scene, 이미지/영상 URL을 생성한다.

백엔드는 Controller → Service → Repository → Database 구조를 따른다.

2. Architecture
Frontend (React / Postman)
        │
        ▼
ProjectController
        │
        ▼
ProjectServiceImpl
        │
        ▼
ProjectRepository
        │
        ▼
Database (H2)

Scene 관련 기능은 다음 흐름을 사용한다.

SceneController
        │
        ▼
SceneServiceImpl
        │
        ▼
SceneRepository
        │
        ▼
Database

AI 관련 기능은 별도의 서비스에서 처리된다.

ProjectController
        │
        ▼
AiService
        │
        ▼
MockAiServiceImpl
3. Layer Structure
Controller

HTTP 요청을 처리하고 Service 계층으로 전달한다.

ProjectController

SceneController

Service

비즈니스 로직을 처리한다.

ProjectServiceImpl

SceneServiceImpl

Repository

데이터베이스 접근을 담당한다.

ProjectRepository

SceneRepository

Entity

데이터베이스 테이블과 매핑되는 도메인 객체.

Project

Scene

DTO

API 요청 및 응답 데이터를 전달하는 객체.

ProjectCreateRequest

SceneCreateRequest

ProjectResponse

SceneResponse

AI Service

AI 기능을 담당하는 서비스.

현재는 실제 모델 대신 MockAiServiceImpl이 사용된다.

4. Core Components
ProjectController

프로젝트 관련 API를 처리한다.

프로젝트 생성

프로젝트 조회

AI 기획 생성

Scene 생성 요청

ProjectServiceImpl

프로젝트 생성 및 상태 관리 로직을 담당한다.

SceneServiceImpl

Scene 생성 및 수정 로직을 담당한다.

MockAiServiceImpl

AI 기능을 대신하는 Mock 서비스.

기획안 3개 생성

Scene 5개 생성

이미지 URL 생성

영상 URL 생성

ProjectRepository

Project 엔티티에 대한 CRUD 처리.

SceneRepository

Scene 데이터를 조회하고
프로젝트별 Scene 목록을 관리한다.

5. API Flow Example
Create Project
Frontend
   │
   ▼
POST /api/projects
   │
   ▼
ProjectController
   │
   ▼
ProjectServiceImpl
   │
   ▼
ProjectRepository
   │
   ▼
Database

응답은 ApiResponse<ProjectResponse> 형태로 반환된다.

Generate AI Plan
Frontend
   │
   ▼
POST /api/projects/{projectId}/plan
   │
   ▼
ProjectController
   │
   ▼
MockAiServiceImpl
   │
   ▼
AI Planning Result

Mock AI 서비스가 기획안 3개를 생성하여 반환한다.

6. Data Structure

Project와 Scene은 1:N 관계를 가진다.

Project (1)
   │
   └── Scene (N)
Project
id
title
idea
style
ratio
planningStatus
createdAt
updatedAt
Scene
id
project
sceneOrder
title
description
coreElements
optionalElements
imagePrompt
videoPrompt
status
createdAt
updatedAt
7. Architecture Notes

현재 구조는 MVP 구현에 적합한 단순한 구조로 설계되어 있다.

장점

Controller / Service / Repository 역할이 명확함

Mock AI 서비스로 개발 및 테스트 가능

DTO를 사용하여 API 응답 구조를 분리함

개선 가능 사항

Entity ↔ DTO 변환 로직을 Mapper로 분리

AI 서비스 인터페이스 구조 정리

SceneController 단순화 가능