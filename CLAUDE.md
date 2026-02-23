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
```

`Application` holds the cover letter text. `Experience` objects represent extracted spans within that text (via character indices), ranked by the AI. Only one `Experience` per `Application` can be `isSelected = true` (enforced by a DB unique index).

### Layer Structure

- **`controller/`** — `ApplicationController`: REST endpoints, input validation via Jakarta Validation
- **`service/`** — `ApplicationService` (workflow orchestration), `ChatGptService` (Spring AI ChatClient wrapper, `@Primary`), `ExperienceExtractionService` (interface)
- **`entity/`** — JPA entities: `Application`, `Experience`
- **`domain/`** — `ApplicationStatus` enum
- **`repository/`** — Spring Data JPA repositories with custom queries
- **`dto/`** — Request/response DTOs

### Implemented Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/applications` | Submit cover letter; triggers GPT experience extraction |
| `POST` | `/api/v1/applications/{id}/select-experience` | Select the top-ranked experience |

Remaining endpoints (follow-up questions, answers, review, interview questions) are planned but not yet implemented.

### Spring AI Integration

`ChatGptService` uses Spring AI's `ChatClient` (via `spring-ai-starter-model-openai`, BOM `2.0.0-M2`). The API key is configured via `spring.ai.openai.api-key` in `application.yaml`, bound to the env var `CHATGPT_API_KEY`. Model options (model name, temperature, max-tokens) are set in `application.yaml` under `spring.ai.openai.chat.options`.

Prompt templates are inlined in `ChatGptService` with a 1-shot example cover letter loaded from `src/main/resources/prompt/experience-extraction-example-coverletter.txt`. The service extracts experiences (each with `title`, `startIdx`, `endIdx`, `rankScore`) and returns only the top-ranked one.

### Database

- MySQL 8.4.7 via Docker Compose (`compose.yaml`); auto-starts with `bootRun`
- Flyway migrations in `src/main/resources/db/migration/`
- V1: `application` table; V2: `experience` table with FK to `application`
- **Never modify merged migration files** — always add a new `V{n}__description.sql`
- `ddl-auto: validate` — schema must match entities exactly

### Testing Approach

Integration tests in `src/integrationTest/java/` use Testcontainers (MySQL 8.4.7). `ExperienceExtractionService` is mocked with `@MockitoBean` to avoid real API calls. The test fixture `example-request.json` lives in `src/integrationTest/resources/`. A placeholder API key (`spring.ai.openai.api-key`) must be set via `DynamicPropertySource` because Spring AI's auto-configuration validates its presence even when mocked.

Unit tests are in `src/test/java/`.

### CI/CD

GitHub Actions (`.github/workflows/ci.yml`) runs three jobs: `unit-test` → `integration-test` → `build` (without tests). Triggers on PRs and pushes to `main`.

### PR Convention

PRs must document: added API/endpoints, DB (Flyway) changes, and status transition changes (per `.github/pull_request_template.md`). One PR per status transition is the intended granularity.
