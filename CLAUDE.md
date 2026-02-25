# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 언어 및 커뮤니케이션 규칙
- 기본 응답 언어: 한국어
- 코드 주석: 한국어로 작성
- 커밋 메시지: 영어로 작성
- 문서화: 한국어로 작성
- 변수명/함수명: 영어 (코드 표준 준수)

## Commands

```bash
# Build
./gradlew build -x test        # Build without tests
./gradlew build                # Full build including all tests

# Run
./gradlew bootRun              # Starts app (auto-starts MySQL via Docker Compose)

# Tests
./gradlew test                 # Unit tests only
./gradlew integrationTest      # Integration tests (requires Docker running)
./gradlew check                # All tests

# Single test class
./gradlew integrationTest --tests "com.khuda.khuda_clue_api.controller.ApplicationControllerTest"
./gradlew test --tests "com.khuda.khuda_clue_api.SomeUnitTest"
```

**Prerequisites:** Java 21, Docker (for MySQL and integration tests), a `.env` file copied from `.env.example` with `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `MYSQL_*` vars, and `CHATGPT_API_KEY`.

## Architecture

This is a Spring Boot 4.0.2 / Java 21 REST API that uses GPT-4o-mini to help recruiters evaluate cover letters through a 3-stage AI pipeline: experience extraction → STAR follow-up questions → evaluation summary.

### Domain Model

```
Application (status: SUBMITTED → EXPERIENCE_SELECTED → QUESTIONS_SENT → ANSWERED → REVIEW_READY)
  └── Experience[] (title, startIdx, endIdx in cover letter text, rankScore, isSelected)
        └── FollowupQuestion[] (type: S/T/A/R, questionText)
```

`Application` holds the cover letter text. `Experience` objects represent extracted spans within that text (via character indices), ranked by the AI. Only one `Experience` per `Application` can be `isSelected = true` (enforced by a DB unique index). `FollowupQuestion` stores STAR-framework questions tied to the selected experience; each experience can have at most one question per type (enforced by a DB unique index).

### Layer Structure

- **`controller/`** — `ApplicationController`: REST endpoints, input validation via Jakarta Validation
- **`service/`** — `ApplicationService` (workflow orchestration), `ChatGptService` (Spring AI ChatClient wrapper, `@Primary`, implements `ExperienceExtractionService`, `FollowupQuestionGenerationService`, and `InterviewRecommendationService`), `ExperienceExtractionService` (interface), `FollowupQuestionGenerationService` (interface), `InterviewRecommendationService` (interface)
- **`entity/`** — JPA entities: `Application`, `Experience`, `FollowupQuestion`, `FollowupAnswer`
- **`domain/`** — `ApplicationStatus` enum, `QuestionType` enum (S, T, A, R)
- **`repository/`** — Spring Data JPA repositories with custom queries; `ApplicationRepository` includes `findByStatusOrderByIdAsc` and `findByStatusAndIdGreaterThanOrderByIdAsc` for cursor pagination
- **`dto/`** — Request/response DTOs; `ApplicationListResponse` / `ApplicationListItemDto` for the review queue list

### Implemented Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET`  | `/api/v1/applications` | List applications by status with cursor-based pagination (`status`, `limit`, `cursor` query params; default status=REVIEW_READY, limit=50) |
| `POST` | `/api/v1/applications` | Submit cover letter; triggers GPT experience extraction |
| `POST` | `/api/v1/applications/{id}/select-experience` | Select the top-ranked experience |
| `POST` | `/api/v1/applications/{id}/generate-followup-questions` | Generate 4 STAR follow-up questions for the selected experience |
| `POST` | `/api/v1/applications/{id}/followup-answers` | Submit STAR answers; auto-generates interview recommendations → REVIEW_READY |

Remaining endpoints (interview questions re-generation) are planned but not yet implemented.

#### 커서 기반 페이지네이션 (`GET /api/v1/applications`)
- `cursor`: Base64 인코딩된 마지막 `id` (없으면 첫 페이지)
- `limit`: 1~100 범위, 초과 시 400 반환
- `limit+1`개 조회 후 초과분이 있으면 `nextCursor` 반환, 없으면 `null`
- 응답: `ApplicationListResponse { items: ApplicationListItemDto[], nextCursor: String | null }`
- `ApplicationListItemDto` 필드: `applicationId`, `applicantId`, `status`, `createdAt`

### Spring AI Integration

`ChatGptService` uses Spring AI's `ChatClient` (via `spring-ai-starter-model-openai`, BOM `2.0.0-M2`). The API key is configured via `spring.ai.openai.api-key` in `application.yaml`, bound to the env var `CHATGPT_API_KEY`. Model options (model name, temperature, max-tokens) are set in `application.yaml` under `spring.ai.openai.chat.options`.

Prompt templates are inlined in `ChatGptService`:
- **Experience extraction**: 1-shot prompt with an example cover letter from `src/main/resources/prompt/experience-extraction-example-coverletter.txt`. Returns experiences (each with `title`, `startIdx`, `endIdx`, `rankScore`); only the top-ranked is saved as `isSelected = true`.
- **STAR follow-up question generation**: Generates exactly 4 questions (one per STAR type: S, T, A, R) for the selected experience text (extracted via `startIdx`/`endIdx`).
- **Interview recommendation generation**: After STAR answers are submitted, generates 3 interview questions targeting unclear/unverified points; result is stored as JSON in `application.interview_recommendations_json`.

### Database

- MySQL 8.4.7 via Docker Compose (`compose.yaml`); auto-starts with `bootRun`
- Flyway migrations in `src/main/resources/db/migration/`
- V1: `application` table; V2: `experience` table with FK to `application`; V3: `followup_question` table with FK to `experience`; V4: `followup_answer` table with FK to `followup_question`
- **Never modify merged migration files** — always add a new `V{n}__description.sql`
- `ddl-auto: validate` — schema must match entities exactly

### Testing Approach

Integration tests in `src/integrationTest/java/` use Testcontainers (MySQL 8.4.7). `ExperienceExtractionService`, `FollowupQuestionGenerationService`, and `InterviewRecommendationService` are all mocked with `@MockitoBean` to avoid real API calls. The test fixture `example-request.json` lives in `src/integrationTest/resources/`. A placeholder API key (`spring.ai.openai.api-key`) must be set via `DynamicPropertySource` because Spring AI's auto-configuration validates its presence even when mocked.

Unit tests are in `src/test/java/`.

### CI/CD

GitHub Actions (`.github/workflows/ci.yml`) runs three jobs: `unit-test` → `integration-test` → `build` (without tests). Triggers on PRs and pushes to `main`.

### PR Convention

PRs must document: added API/endpoints, DB (Flyway) changes, and status transition changes (per `.github/pull_request_template.md`). One PR per status transition is the intended granularity.
