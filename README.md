# CLUE

자기소개서 서류 평가 검증 파이프라인 API

![Java](https://img.shields.io/badge/Java-21-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.2-brightgreen)

---

## 개요

생성형 AI 시대에 자기소개서의 문장 품질과 실제 경험 수준이 분리되었다. CLUE는 자기소개서 전체를 읽지 않아도, 핵심 경험 1개를 구조화된 질문으로 검증해 평가자가 면접 전 판단할 수 있도록 만드는 3단계 AI 파이프라인이다.

| 단계 | 역할 |
|------|------|
| **경험 추출** | 자기소개서에서 검증 효율이 가장 높은 경험 1개를 선택 |
| **STAR 검증** | 선택된 경험을 구조화된 질문 4개로 분해해 검증 가능한 정보 확보 |
| **결과 압축** | 경험 원문 + STAR 답변 + 면접 추천 질문을 한 화면에 제공 |

---

## 시작하기

**요구사항**: Java 21, Docker Desktop

```bash
# 환경 변수 설정
cp .env.example .env
# .env에 DB_URL, DB_USERNAME, DB_PASSWORD, MYSQL_*, CHATGPT_API_KEY 입력

# 빌드 및 실행 (MySQL 자동 시작)
./gradlew build -x test
./gradlew bootRun
```

```bash
# 헬스 체크
curl http://localhost:8080/actuator/health
```

---

## API

전체 요청/응답 스펙은 [`doc/docs/API_SPEC.md`](doc/docs/API_SPEC.md)를 참고한다.

### 엔드포인트

| Method | Endpoint | 설명 | 상태 전이 |
|--------|----------|------|-----------|
| `POST` | `/api/v1/applications` | 지원서 제출 | → `SUBMITTED` |
| `POST` | `/api/v1/applications/{id}/select-experience` | 경험 선택 | → `EXPERIENCE_SELECTED` |
| `POST` | `/api/v1/applications/{id}/generate-followup-questions` | STAR 질문 생성 | → `QUESTIONS_SENT` |
| `POST` | `/api/v1/applications/{id}/followup-answers` | STAR 답변 제출 | → `REVIEW_READY` |
| `GET`  | `/api/v1/applications` | 평가 대기 목록 조회 | — |

각 API는 지정된 상태에서만 호출 가능하며, 성공 시 다음 상태로 전이된다.

### 상태 흐름

```
SUBMITTED → EXPERIENCE_SELECTED → QUESTIONS_SENT → ANSWERED → REVIEW_READY
```

### GET /api/v1/applications 파라미터

| 파라미터 | 기본값 | 설명 |
|---------|--------|------|
| `status` | `REVIEW_READY` | 조회할 상태 |
| `limit` | `50` (최대 100) | 페이지 크기 |
| `cursor` | — | 이전 응답의 `nextCursor` (Base64, 없으면 첫 페이지) |

---

## 아키텍처

### 데이터 모델

```
Application (상태 + 자기소개서 원문 + 면접 추천 질문 JSON)
└── Experience[] (startIdx/endIdx로 원문 범위 참조, 1개만 선택됨)
      └── FollowupQuestion[] (S/T/A/R 유형별 질문 1개)
            └── FollowupAnswer (지원자 답변)
```

### 기술 스택

| | |
|---|---|
| Runtime | Java 21, Spring Boot 4.0.2 |
| Database | MySQL 8.4.7, Spring Data JPA, Flyway |
| AI | Spring AI 2.0.0-M2, OpenAI GPT-4o-mini |
| Testing | JUnit 5, Testcontainers |
| Build | Gradle 9.3.0 |

### 프로젝트 구조

```
src/main/java/.../khuda_clue_api/
├── controller/   # REST 엔드포인트
├── service/      # 워크플로우 조율, GPT 연동
├── entity/       # JPA 엔티티
├── repository/   # Spring Data JPA
├── dto/          # 요청/응답 DTO
└── domain/       # ApplicationStatus, QuestionType 열거형

src/main/resources/
├── db/migration/ # Flyway 마이그레이션 (V1~V4)
└── prompt/       # GPT 프롬프트 템플릿

src/integrationTest/  # Testcontainers 통합 테스트
```

---

## 개발

### 테스트

```bash
./gradlew test              # 단위 테스트
./gradlew integrationTest   # 통합 테스트 (Docker 필요)
./gradlew check             # 전체
```

### 브랜치 전략

`main`은 항상 배포 가능한 상태를 유지한다.

| 접두사 | 용도 |
|--------|------|
| `feat/` | 기능 개발 |
| `fix/` | 버그 수정 |
| `chore/` | 환경·의존성·리팩터 |
| `exp/` | 실험 |

### PR 규칙

상태 전이 1단계 = PR 1개. PR 설명에 아래 세 항목을 명시한다.

```
1. 추가한 API / 엔드포인트
2. DB(Flyway) 변경 여부
3. 상태 전이 변화 (예: QUESTIONS_SENT → REVIEW_READY)
```

### DB 마이그레이션

머지된 Flyway 파일은 수정하지 않는다. 새 변경은 반드시 새 파일(`V{n}__description.sql`)로 추가한다.
