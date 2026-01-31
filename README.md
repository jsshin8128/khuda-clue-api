# CLUE

> **Uncover evidence, elevate the applicant's cover letter fast.**

생성형 AI 시대에 서류 평가의 신뢰를 복원하는 3단계 검증 시스템

---

## 문제 정의

생성형 AI로 자기소개서가 누구나 '완벽해 보이게' 작성될 수 있게 되면서:

- **텍스트만으로는 진짜 경험인지 판단 불가**
- **서류 처리량 증가로 검증 비용 급증증**
- **평가자들이 보수적 판단 또는 추가 검증 요구로 인한 레몬 마켓 구조**

→ **좋은 지원자도 손해, 전체 선발 효율 저하**

---

## 해결 방법: 3단계 검증 프로세스

### 1. 경험 추출·랭킹 
- 자소서 전체를 읽는 비용을 줄이기 위해 **검증 가치가 가장 큰 경험 1개만 선택**
- Goal-Action-Result 구조가 명확한 경험을 우선 선택
- **모델**: GPT 계열 (분류/랭킹) + 프롬프트 엔지니어링

### 2️. STAR 후속 질문 생성
- 선택된 경험을 STAR 구조로 분해한 **맞춤형 질문 4개** 자동 생성
- 지원자로부터 짧고 구체적인 답변을 받아 **검증 가능한 정보 단위** 확보
- **모델**: GPT 계열 (생성)

### 3️. 종합 평가 자료 제공 
- 선택된 경험 원문 + STAR 답변 + 면접 추천 질문을 **한 화면에 패키징**
- 평가자가 자소서를 처음부터 끝까지 읽지 않아도 **서류 단계에서 즉시 판단** 가능

---

## 시스템 아키텍처

```
지원서 제출 → 경험 1개 선택 → STAR 질문 생성 → 답변 제출 → 평가자 리뷰
   ↓              ↓                ↓              ↓            ↓
SUBMITTED → EXPERIENCE_SELECTED → QUESTIONS_SENT → ANSWERED → REVIEW_READY
```

### 핵심 데이터 모델
- **application**: 지원서 원문 + 상태 관리
- **experience**: 경험 후보들 (top3 저장, 1개 선택)
- **followup_question**: STAR 질문 4개 (S/T/A/R)
- **followup_answer**: 지원자의 답변
- **interview_recommendations_json**: 면접 추천 질문 (JSON)

---

## 기술 스택

- **Backend**: Spring Boot 4.0.2, Java 21
- **Database**: MySQL 8.4.7 (InnoDB)
- **Migration**: Flyway
- **Build Tool**: Gradle 9.3.0
- **Testing**: Testcontainers
- **Monitoring**: Spring Boot Actuator

---

## 빠른 시작

### 사전 요구사항
- Java 21
- Docker Desktop

### 실행

```bash
# 1. 환경 변수 설정 (.env.local 파일 생성)
cp .env.example .env.local
# .env.local 파일에 DB 정보 입력

# 2. 빌드
./gradlew build -x test

# 3. 실행 (Docker Compose 자동 시작)
./gradlew bootRun
```

### 헬스 체크

```bash
curl http://localhost:8080/actuator/health
```

---

## API 엔드포인트

| Method | Endpoint | 설명 | 상태 전이 |
|--------|----------|------|-----------|
| `POST` | `/api/v1/applications` | 지원서 제출 | → `SUBMITTED` |
| `POST` | `/api/v1/applications/{id}/select-experience` | 경험 1개 선택 | → `EXPERIENCE_SELECTED` |
| `POST` | `/api/v1/applications/{id}/generate-followup-questions` | STAR 질문 생성 | → `QUESTIONS_SENT` |
| `POST` | `/api/v1/applications/{id}/followup-answers` | 답변 제출 (자동 추천 생성) | → `REVIEW_READY` |
| `GET` | `/api/v1/applications?status=REVIEW_READY` | 평가자 큐 목록 | - |
| `GET` | `/api/v1/applications/{id}/review` | 평가자 결과 조회 (한 화면 완성) | - |
| `POST` | `/api/v1/applications/{id}/recommend-interview-questions` | 면접 질문 재생성 | - |

> **상태 전이 규칙**: 각 API는 정해진 상태에서만 실행 가능하며, 성공 시 다음 상태로 업데이트됩니다.

---

## 개발 가이드

### 브랜치 전략 (Trunk Based Development)

- **main**: 항상 정상 동작 상태 유지
- **feat/**: 기능 개발 (예: `feat/applications-submit`)
- **fix/**: 버그 수정
- **chore/**: 환경/리팩터/의존성
- **exp/**: 실험 

### PR 규칙

**원칙**: **상태 전이 1단계 = PR 1개**

| PR | API | 상태 전이 |
|----|-----|-----------|
| PR1 | `POST /applications` | → `SUBMITTED` |
| PR2 | `POST /applications/{id}/select-experience` | → `EXPERIENCE_SELECTED` |
| PR3 | `POST /applications/{id}/generate-followup-questions` | → `QUESTIONS_SENT` |
| PR4 | `POST /applications/{id}/followup-answers` | → `REVIEW_READY` |
| PR5 | `GET /applications?status=REVIEW_READY` | - |
| PR6 | `GET /applications/{id}/review` | - |
| PR7 | `POST /applications/{id}/recommend-interview-questions` | - |

### PR 머지 조건

**CI 통과**
- Unit Test (`./gradlew test`) 통과
- Integration Test (`./gradlew integrationTest`) 통과
- Build (`./gradlew build -x test`) 통과

### Flyway 규칙

**수정하지 않고 추가만 할 것**
- 새 변경은 항상 새 파일 생성 (예: `V4__create_followup_answer.sql`)
- 이미 머지된 Flyway 파일은 절대 고치지 않음

### PR 작성 시 필수 3줄

```
1. 이 PR이 추가한 API / 엔드포인트:
2. DB(Flyway) 변경 여부: (있음/없음)
3. 상태 전이 변화: (예: QUESTIONS_SENT → REVIEW_READY)
```

---

## 프로젝트 구조

```
khuda-clue-api/
├── src/main/java/com/khuda/khuda_clue_api/
│   ├── application/          # 지원서 도메인
│   ├── experience/           # 경험 도메인
│   ├── followup/             # STAR 질문/답변 도메인
│   └── review/               # 평가자 리뷰 도메인
├── src/main/resources/
│   ├── application.yaml
│   └── db/migration/         # Flyway 마이그레이션
├── src/integrationTest/      # 통합 테스트 (Testcontainers)
├── compose.yaml              # Docker Compose 설정
└── build.gradle.kts
```

---

## 배경

이 프로젝트는 **경희대학교 데이터분석/AI 중앙동아리 KHUDA** 부회장으로 활동하며, 부원 선발 과정에서 겪은 실제 문제를 해결하기 위해 시작되었습니다.

**핵심 인사이트**: 생성형 AI 시대에는 "AI 사용을 막는 것"이 아니라, **"서류 단계에서 평가자가 더 빠르고 정확하게 판단하도록 신뢰를 복원하는 검증 절차를 최소 비용으로 넣는 것"**이 해결책입니다.

---

## 라이선스

이 프로젝트는 개인적으로 진행하는 KHUDA 운영진 프로젝트입니다.
