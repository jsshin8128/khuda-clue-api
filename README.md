<div align="center">
  <img src="https://github.com/user-attachments/assets/d2300a99-94b2-4c39-a196-a88a249b2e4c" alt="CLUE" width="600" />
  <br/>
  <p><i>From Cover Letters to Uncovered Experience</i></p>
  <p><b>효율적인 서류 평가를 위한 새로운 기준</b> · AI 기반 서류 평가 보조 서비스 CLUE</p>

  [![CI](https://github.com/jsshin8128/khuda-clue-api/actions/workflows/ci.yml/badge.svg)](https://github.com/jsshin8128/khuda-clue-api/actions/workflows/ci.yml)
  ![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)
  ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.2-6DB33F?logo=springboot&logoColor=white)
  ![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0.0--M2-6DB33F?logo=spring&logoColor=white)
  ![MySQL](https://img.shields.io/badge/MySQL-8.4.7-4479A1?logo=mysql&logoColor=white)
  ![Gradle](https://img.shields.io/badge/Gradle-9.3.0-02303A?logo=gradle&logoColor=white)
  ![Testcontainers](https://img.shields.io/badge/Testcontainers-✓-1AB394)
</div>

---

## Overview

생성형 AI로 자기소개서 문장 품질과 실제 경험이 분리된 환경에서, 평가자가 **최소 비용으로 판단**할 수 있도록 설계한 3단계 검증 파이프라인이다.

| 단계 | 역할 |
|------|------|
| **① 경험 추출** | 자소서에서 검증 효율이 가장 높은 경험 1개를 GPT로 선택 |
| **② STAR 검증** | 선택 경험을 구조화 질문 4개로 분해해 검증 가능한 정보 확보 |
| **③ 결과 압축** | 경험 원문 + STAR 답변 + 면접 추천 질문을 한 화면에 제공 |

---

## Getting Started

**Prerequisites**: Java 21, Docker Desktop, OpenAI API Key

```bash
cp .env.example .env
# .env에 DB_URL, DB_USERNAME, DB_PASSWORD, MYSQL_*, CHATGPT_API_KEY 입력

./gradlew build -x test
./gradlew bootRun
```

```bash
curl http://localhost:8080/actuator/health
```

---

## API

전체 스펙: [`doc/docs/API_SPEC.md`](doc/docs/API_SPEC.md)

| Method | Endpoint | 설명 | 상태 전이 |
|--------|----------|------|-----------|
| `POST` | `/api/v1/applications` | 지원서 제출 | → `SUBMITTED` |
| `POST` | `/api/v1/applications/{id}/select-experience` | 경험 추출·선택 | → `EXPERIENCE_SELECTED` |
| `POST` | `/api/v1/applications/{id}/generate-followup-questions` | STAR 질문 생성 | → `QUESTIONS_SENT` |
| `POST` | `/api/v1/applications/{id}/followup-answers` | STAR 답변 제출 + 추천 자동 생성 | → `REVIEW_READY` |
| `GET`  | `/api/v1/applications` | 평가 대기 목록 (커서 페이지네이션) | — |
| `GET`  | `/api/v1/applications/{id}/review` | 평가자 결과 패키지 단건 조회 | — |
| `POST` | `/api/v1/applications/{id}/recommend-interview-questions` | 면접 추천 질문 재생성 | — |

**상태 흐름**

```
SUBMITTED → EXPERIENCE_SELECTED → QUESTIONS_SENT → ANSWERED → REVIEW_READY
```

각 API는 지정된 상태에서만 호출 가능하며, 위반 시 `409 Conflict`를 반환한다.

---

## Architecture

**데이터 모델**

```
Application (status, coverLetterText, interviewRecommendationsJson)
└── Experience (startIdx, endIdx, rankScore, isSelected)
      └── FollowupQuestion (type: S/T/A/R)
            └── FollowupAnswer
```

**기술 스택**

| 영역 | 기술 |
|------|------|
| Runtime | Java 21, Spring Boot 4.0.2 |
| Database | MySQL 8.4.7, Spring Data JPA, Flyway |
| AI | Spring AI 2.0.0-M2, OpenAI GPT-4o-mini |
| Testing | JUnit 5, Testcontainers (MySQL 8.4.7) |
| Build | Gradle 9.3.0 |
| CI | GitHub Actions (unit-test → integration-test → build) |

**프로젝트 구조**

```
src/main/java/.../khuda_clue_api/
├── controller/   # REST endpoints
├── service/      # 워크플로우 조율, GPT 연동
├── entity/       # JPA entities
├── repository/   # Spring Data JPA
├── dto/          # Request/Response DTOs
└── domain/       # ApplicationStatus, QuestionType enums

src/main/resources/
├── db/migration/ # Flyway (V1~V4)
└── prompt/       # GPT 프롬프트 템플릿

src/integrationTest/  # Testcontainers 통합 테스트
```

---

## Development

**테스트**

```bash
./gradlew test              # 단위 테스트
./gradlew integrationTest   # 통합 테스트 (Docker 필요)
./gradlew check             # 전체
```

**브랜치 전략 (TBD)**

`main`은 항상 배포 가능한 상태. 작업은 `feat/` → PR → merge 순서로 진행한다.

- 머지 조건: `./gradlew test` + `./gradlew integrationTest` 통과
- PR 단위: 상태 전이 1단계 = PR 1개
- Flyway: 기존 파일 수정 금지, 신규 `V{n}__description.sql` 추가만 허용
