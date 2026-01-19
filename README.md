# CLUE - 서류 검증 AI 시스템

> Uncover evidence, elevate the applicant's cover letter fast.

## 프로젝트 개요

경희대학교 데이터분석/AI 중앙동아리 **KHUDA** 부회장으로 활동하며, 부원 선발 과정에서 **면접까지 가지 않아도 되는 지원자를 서류 단계에서 더 빠르고 정확하게 구분할 필요**를 느꼈습니다.

생성형 AI의 확산으로 자기소개서가 짧은 시간 안에 누구나 '완벽해 보이게' 작성될 수 있게 되면서, 텍스트만으로는 지원자의 경험이 **진짜인지(실제로 했는지) 판단하기 어려워졌고**, 동시에 지원서 처리량이 많아 모든 내용을 꼼꼼히 읽고 검증하는 데 드는 비용도 급격히 커졌습니다.

이 문제를 해결하기 위해, **서류 단계에서 신뢰를 최소 비용으로 복원하는 3단계 검증 프로세스**를 설계했습니다.

## 주요 기능

### 3단계 검증 프로세스

1. **경험 추출 및 선택**
   - 자기소개서를 훑어 후속 검증에 가장 적합한 경험 1개를 추출
   - Goal-Action-Result 구조가 명확한 경험을 선택해 평가 대상을 1개로 고정
   - 읽기 비용을 최소화

2. **맞춤형 후속 질문 생성**
   - 선택된 경험을 STAR 구조로 분해한 **맞춤형 후속 질문** 자동 생성
   - 지원자로부터 짧고 구체적인 답변을 받아 **검증 가능한 정보 단위** 확보

3. **종합 평가 자료 제공**
   - 선택된 경험 원문, STAR 답변, 남는 불확실성을 찌르는 면접 추천 질문 제공
   - 평가자가 자기소개서를 처음부터 끝까지 읽지 않더라도 **서류 단계에서 즉시 의사결정** 가능

## 기술 스택

- **Backend**: Spring Boot 4.0.1, Java 21
- **Database**: MySQL 8.4.7 (Docker Compose)
- **Migration**: Flyway
- **Build Tool**: Gradle 9.3.0
- **Monitoring**: Spring Boot Actuator

## 시작하기

### 사전 요구사항
- Java 21
- Docker Desktop

### 빌드 및 실행

```bash
# 의존성 설치 및 빌드
./gradlew build -x test

# 애플리케이션 실행 (Docker Compose 자동 시작)
./gradlew bootRun
```

### 헬스 체크

```bash
curl http://localhost:8080/actuator/health
```

## 프로젝트 구조

```
khuda-clue-api/
├── src/main/java/com/khuda/khuda_clue_api/
│   └── KhudaClueApiApplication.java
├── src/main/resources/
│   ├── application.yaml
│   └── db/migration/          # Flyway 마이그레이션 파일들
├── compose.yaml               # Docker Compose 설정
└── build.gradle.kts           # Gradle 빌드 설정
```

## 설정

### 환경 변수 설정

비밀값(DB 비밀번호 등)은 환경별로 다른 파일을 사용하여 관리합니다.

**중요**: 환경 변수 설정은 필수입니다. 환경 변수가 없으면 애플리케이션이 시작되지 않습니다.

#### 로컬 개발 환경

로컬 개발 환경에서는 `.env.local` 파일을 사용합니다. 애플리케이션이 자동으로 `.env.local` 파일을 찾아 로드합니다.

1. 프로젝트 루트에 .env.local 파일이 없다면 .env.example을 참고하여 생성

2. `.env.local` 파일에 실제 비밀값 설정
```bash
DB_URL=jdbc:mysql://localhost:3306/clue_db
DB_USERNAME=your_db_username
DB_PASSWORD=your_db_password
MYSQL_DATABASE=clue_db
MYSQL_USER=your_db_username
MYSQL_PASSWORD=your_db_password
MYSQL_ROOT_PASSWORD=your_root_password
```

#### 배포 환경

배포 환경에서는 `.env` 파일을 사용합니다. `.env.local` 파일이 없으면 자동으로 `.env` 파일을 로드합니다.

- 배포 서버에 `.env` 파일을 배치하고 필요한 환경 변수를 설정하세요.
- 또는 시스템 환경 변수로 직접 설정할 수도 있습니다 (환경 변수가 파일보다 우선순위가 높음).

**주의**: 
- `.env.local`과 `.env` 파일은 모두 `.gitignore`에 포함되어 있어 Git에 커밋되지 않습니다.
- 로컬에서는 `.env.local`이 우선적으로 사용되고, 없으면 `.env`를 사용합니다.

#### Docker Compose와 환경 변수

이 프로젝트는 Spring Boot의 Docker Compose 지원 기능을 사용합니다 (`spring.docker.compose.enabled: true`).

**Spring Boot로 실행하는 경우** (`./gradlew bootRun`):
- Spring Boot가 Docker Compose를 자동으로 관리합니다
- `.env.local` 파일의 환경 변수가 자동으로 로드되어 Docker Compose 컨테이너에 적용됩니다
- 별도의 옵션이 필요하지 않습니다

**수동으로 Docker Compose 명령을 사용하는 경우**:
- Docker Compose는 기본적으로 `.env` 파일만 자동으로 읽습니다
- `.env.local` 파일을 사용하려면 `--env-file` 옵션을 명시해야 합니다:
  ```bash
  # 컨테이너 시작
  docker compose --env-file .env.local up -d
  
  # 컨테이너 중지
  docker compose --env-file .env.local down
  
  # 컨테이너 재시작
  docker compose --env-file .env.local restart
  ```

**컨테이너 재시작이 필요한 경우**:
- `compose.yaml` 파일 변경 시 (포트, 환경 변수, 이미지 버전 등)
- `.env.local` 파일의 MySQL 관련 환경 변수 변경 시
- 데이터베이스를 완전히 초기화해야 할 때 (`docker compose down -v` 후 재시작)

### application.yaml 주요 설정
- **Docker Compose**: MySQL 데이터베이스 자동 관리
- **JPA**: Hibernate validate 모드 (Flyway 우선)
- **Actuator**: health, info 엔드포인트 노출
- **Environment Variables**: DB 연결 정보는 환경 변수로 관리 (환경 변수 필수)

## API 엔드포인트

- `GET /actuator/health` - 애플리케이션 헬스 체크
- `GET /actuator/info` - 애플리케이션 정보

## 기여하기

개인적으로 개발 중입니다. KHUDA에 기여할 예정입니다.

## 라이선스

이 프로젝트는 KHUDA 내부 프로젝트입니다.