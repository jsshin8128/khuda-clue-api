package com.khuda.khuda_clue_api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.khuda.khuda_clue_api.domain.ApplicationStatus;
import com.khuda.khuda_clue_api.domain.QuestionType;
import com.khuda.khuda_clue_api.dto.request.AnswerItem;
import com.khuda.khuda_clue_api.dto.request.FollowupAnswersRequest;
import com.khuda.khuda_clue_api.dto.request.SubmitRequest;
import com.khuda.khuda_clue_api.dto.response.FollowupAnswersResponse;
import com.khuda.khuda_clue_api.dto.response.GenerateFollowupQuestionsResponse;
import com.khuda.khuda_clue_api.dto.response.SelectExperienceResponse;
import com.khuda.khuda_clue_api.dto.response.SubmitResponse;
import com.khuda.khuda_clue_api.entity.Experience;
import com.khuda.khuda_clue_api.entity.FollowupAnswer;
import com.khuda.khuda_clue_api.entity.FollowupQuestion;
import com.khuda.khuda_clue_api.repository.ApplicationRepository;
import com.khuda.khuda_clue_api.repository.ExperienceRepository;
import com.khuda.khuda_clue_api.repository.FollowupAnswerRepository;
import com.khuda.khuda_clue_api.repository.FollowupQuestionRepository;
import com.khuda.khuda_clue_api.service.ExperienceExtractionService;
import com.khuda.khuda_clue_api.service.FollowupQuestionGenerationService;
import com.khuda.khuda_clue_api.service.InterviewRecommendationService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import com.khuda.khuda_clue_api.dto.response.ApplicationListResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ApplicationControllerTest {

    @Container
    static MySQLContainer mysql = new MySQLContainer(DockerImageName.parse("mysql:8.4.7"))
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @BeforeAll
    static void checkDockerAvailability() {
        Process process = null;
        try {
            process = new ProcessBuilder("docker", "--version").start();
            if (process.waitFor() != 0) {
                fail("Docker가 사용 가능하지 않습니다. Docker를 설치하고 실행해주세요.");
            }
        } catch (IOException | InterruptedException e) {
            fail("Docker 가용성 확인 중 오류가 발생했습니다: " + e.getMessage());
        } finally {
            if (process != null && process.isAlive()) {
                process.destroy();
            }
        }
    }

    @DynamicPropertySource
    static void configureProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        // Spring AI auto-configuration이 API 키를 검증하므로 테스트용 플레이스홀더 설정
        // (ExperienceExtractionService, FollowupQuestionGenerationService는 @MockitoBean으로 모킹)
        registry.add("spring.ai.openai.api-key", () -> "test-placeholder-not-used");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private ExperienceRepository experienceRepository;

    @Autowired
    private FollowupQuestionRepository followupQuestionRepository;

    @Autowired
    private FollowupAnswerRepository followupAnswerRepository;

    @MockitoBean
    private ExperienceExtractionService experienceExtractionService;

    @MockitoBean
    private FollowupQuestionGenerationService followupQuestionGenerationService;

    @MockitoBean
    private InterviewRecommendationService interviewRecommendationService;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private SubmitRequest loadExampleRequest() throws IOException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("example-request.json")) {
            if (inputStream == null) {
                throw new IOException("example-request.json 파일을 찾을 수 없습니다.");
            }
            String jsonContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return objectMapper.readValue(jsonContent, SubmitRequest.class);
        }
    }

    // =========================================================
    // PR1: 지원서 제출 테스트
    // =========================================================

    @Test
    @DisplayName("지원서 제출 API가 정상적으로 작동한다")
    void submitApplication_shouldReturn201WithValidResponse() throws Exception {
        // Given
        SubmitRequest request = loadExampleRequest();

        // When & Then
        MvcResult result = mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.applicationId").exists())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn();

        // 추가 검증
        String responseBody = result.getResponse().getContentAsString();
        SubmitResponse response = objectMapper.readValue(responseBody, SubmitResponse.class);

        assertThat(response.applicationId()).isNotNull();
        assertThat(response.status()).isEqualTo(ApplicationStatus.SUBMITTED);
        assertThat(response.createdAt()).isNotNull();
    }

    @Test
    @DisplayName("필수 필드가 누락되면 400 에러를 반환한다")
    void submitApplication_withMissingFields_shouldReturn400() throws Exception {
        // Given - applicantId 누락
        String invalidRequest = """
                {
                    "coverLetterText": "테스트 자기소개서"
                }
                """;

        // When & Then
        mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("빈 문자열이 입력되면 400 에러를 반환한다")
    void submitApplication_withEmptyFields_shouldReturn400() throws Exception {
        // Given
        SubmitRequest request = new SubmitRequest("", "");

        // When & Then
        mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================
    // PR2: 경험 선택 테스트
    // =========================================================

    @Test
    @DisplayName("경험 선택 API가 정상적으로 작동한다")
    void selectExperience_shouldReturn200WithValidResponse() throws Exception {
        // Given - 먼저 지원서를 제출
        SubmitRequest submitRequest = loadExampleRequest();
        MvcResult submitResult = mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String submitResponseBody = submitResult.getResponse().getContentAsString();
        SubmitResponse submitResponse = objectMapper.readValue(submitResponseBody, SubmitResponse.class);
        Long applicationId = submitResponse.applicationId();

        // Mock experience extraction service response (rankScore 내림차순으로 정렬된 상태)
        // 실제 자소서(example-request.json)의 경험 부분을 기반으로 한 Mock 데이터
        var mockExperiences = List.of(
            Experience.createCandidate(applicationId, "브랜드 론칭 및 매출 신장", 946, 1290, 0.85),
            Experience.createCandidate(applicationId, "콘텐츠팀 인턴 SNS 콘텐츠 제작", 774, 944, 0.75),
            Experience.createCandidate(applicationId, "개인브랜드 운영 및 트렌드 분석", 600, 725, 0.65)
        );
        Mockito.when(experienceExtractionService.extractExperiences(Mockito.eq(applicationId), Mockito.anyString()))
                .thenReturn(mockExperiences);

        // When - 경험 선택 실행
        MvcResult result = mockMvc.perform(post("/api/v1/applications/{applicationId}/select-experience", applicationId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.applicationId").value(applicationId))
                .andExpect(jsonPath("$.status").value("EXPERIENCE_SELECTED"))
                .andExpect(jsonPath("$.selectedExperience.experienceId").exists())
                .andExpect(jsonPath("$.selectedExperience.title").exists())
                .andExpect(jsonPath("$.selectedExperience.startIdx").exists())
                .andExpect(jsonPath("$.selectedExperience.endIdx").exists())
                .andExpect(jsonPath("$.selectedExperience.rankScore").exists())
                .andReturn();

        // Then - 응답 검증
        String responseBody = result.getResponse().getContentAsString();
        SelectExperienceResponse response = objectMapper.readValue(responseBody, SelectExperienceResponse.class);

        assertThat(response.applicationId()).isEqualTo(applicationId);
        assertThat(response.status()).isEqualTo(ApplicationStatus.EXPERIENCE_SELECTED);
        assertThat(response.selectedExperience()).isNotNull();
        assertThat(response.selectedExperience().experienceId()).isNotNull();
        assertThat(response.selectedExperience().rankScore()).isEqualTo(0.85); // 최고 점수 검증

        // 데이터베이스 검증: 경험 후보 3개가 저장되었는지 확인
        List<Experience> savedExperiences = experienceRepository.findByApplicationIdOrderByRankScoreDesc(applicationId);
        assertThat(savedExperiences).hasSize(3);
        
        // 최고 점수 경험이 선택되었는지 확인
        Experience selectedExperience = experienceRepository.findById(response.selectedExperience().experienceId())
                .orElseThrow();
        assertThat(selectedExperience.getIsSelected()).isTrue();
        assertThat(selectedExperience.getRankScore()).isEqualTo(0.85);
        assertThat(selectedExperience.getTitle()).isEqualTo("브랜드 론칭 및 매출 신장");

        // Application 상태가 EXPERIENCE_SELECTED로 변경되었는지 확인
        var application = applicationRepository.findById(applicationId).orElseThrow();
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.EXPERIENCE_SELECTED);
    }

    @Test
    @DisplayName("존재하지 않는 지원서 ID로 경험 선택 시 404 에러를 반환한다")
    void selectExperience_withNonExistentApplication_shouldReturn404() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/applications/{applicationId}/select-experience", 99999L)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("SUBMITTED 상태가 아닌 지원서에서 경험 선택 시 409 에러를 반환한다")
    void selectExperience_withWrongStatus_shouldReturn409() throws Exception {
        // Given - 지원서 제출 후 경험 선택까지 완료
        SubmitRequest submitRequest = loadExampleRequest();
        MvcResult submitResult = mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String submitResponseBody = submitResult.getResponse().getContentAsString();
        SubmitResponse submitResponse = objectMapper.readValue(submitResponseBody, SubmitResponse.class);
        Long applicationId = submitResponse.applicationId();

        // Mock experience extraction service response
        // 실제 자소서(example-request.json)의 경험 부분을 기반으로 한 Mock 데이터
        var mockExperiences = List.of(
            Experience.createCandidate(applicationId, "브랜드 론칭 및 매출 신장", 946, 1290, 0.85)
        );
        Mockito.when(experienceExtractionService.extractExperiences(Mockito.eq(applicationId), Mockito.anyString()))
                .thenReturn(mockExperiences);

        // 경험 선택 실행 (상태를 EXPERIENCE_SELECTED로 변경)
        mockMvc.perform(post("/api/v1/applications/{applicationId}/select-experience", applicationId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // When & Then - 같은 지원서에서 다시 경험 선택 시도 (409 에러 예상)
        mockMvc.perform(post("/api/v1/applications/{applicationId}/select-experience", applicationId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("경험 후보가 없을 때 500 에러를 반환한다")
    void selectExperience_withNoCandidates_shouldReturn500() throws Exception {
        // Given - 지원서 제출
        SubmitRequest submitRequest = loadExampleRequest();
        MvcResult submitResult = mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String submitResponseBody = submitResult.getResponse().getContentAsString();
        SubmitResponse submitResponse = objectMapper.readValue(submitResponseBody, SubmitResponse.class);
        Long applicationId = submitResponse.applicationId();

        // Mock experience extraction service가 빈 리스트 반환
        Mockito.when(experienceExtractionService.extractExperiences(Mockito.eq(applicationId), Mockito.anyString()))
                .thenReturn(List.of());

        // When & Then - 경험 선택 시도 (500 에러 예상)
        mockMvc.perform(post("/api/v1/applications/{applicationId}/select-experience", applicationId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError());
    }

    // =========================================================
    // PR3: STAR 후속 질문 생성 테스트
    // =========================================================

    /**
     * 지원서 제출 → 경험 선택까지 수행하고 experienceId를 반환하는 헬퍼 메서드
     */
    private long submitAndSelectExperience() throws Exception {
        // 지원서 제출
        SubmitRequest submitRequest = loadExampleRequest();
        MvcResult submitResult = mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        SubmitResponse submitResponse = objectMapper.readValue(
                submitResult.getResponse().getContentAsString(), SubmitResponse.class);
        Long applicationId = submitResponse.applicationId();

        // Mock 경험 추출 응답
        var mockExperiences = List.of(
                Experience.createCandidate(applicationId, "브랜드 론칭 및 매출 신장", 946, 1290, 0.85)
        );
        Mockito.when(experienceExtractionService.extractExperiences(Mockito.eq(applicationId), Mockito.anyString()))
                .thenReturn(mockExperiences);

        // 경험 선택
        MvcResult selectResult = mockMvc.perform(post("/api/v1/applications/{applicationId}/select-experience", applicationId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        SelectExperienceResponse selectResponse = objectMapper.readValue(
                selectResult.getResponse().getContentAsString(), SelectExperienceResponse.class);

        return selectResponse.selectedExperience().experienceId();
    }

    @Test
    @DisplayName("STAR 질문 생성 API가 정상적으로 작동한다 - 4개 생성 + QUESTIONS_SENT 상태 전이")
    void generateFollowupQuestions_shouldReturn200WithFourQuestions() throws Exception {
        // Given - 지원서 제출 → 경험 선택 완료
        long experienceId = submitAndSelectExperience();

        // DB에서 applicationId 역으로 조회
        Long applicationId = experienceRepository.findById(experienceId).orElseThrow().getApplicationId();

        // Mock STAR 질문 생성 응답 (S/T/A/R 각 1개씩)
        var mockQuestions = List.of(
                new FollowupQuestion(experienceId, QuestionType.S, "당시 팀 규모와 본인 역할을 구체적으로 적어주세요."),
                new FollowupQuestion(experienceId, QuestionType.T, "해결하려던 문제와 성공 기준을 1개로 적어주세요."),
                new FollowupQuestion(experienceId, QuestionType.A, "본인이 수행한 행동을 3단계로 적어주세요."),
                new FollowupQuestion(experienceId, QuestionType.R, "결과(전후 변화)와 근거 위치를 적어주세요.")
        );
        Mockito.when(followupQuestionGenerationService.generateFollowupQuestions(
                        Mockito.eq(experienceId), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(mockQuestions);

        // When - STAR 질문 생성 API 호출
        MvcResult result = mockMvc.perform(post("/api/v1/applications/{applicationId}/generate-followup-questions", applicationId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.applicationId").value(applicationId))
                .andExpect(jsonPath("$.status").value("QUESTIONS_SENT"))
                .andExpect(jsonPath("$.experienceId").value(experienceId))
                .andExpect(jsonPath("$.questions").isArray())
                .andExpect(jsonPath("$.questions.length()").value(4))
                .andExpect(jsonPath("$.questions[0].questionId").exists())
                .andExpect(jsonPath("$.questions[0].type").exists())
                .andExpect(jsonPath("$.questions[0].questionText").exists())
                .andReturn();

        // Then - 응답 검증
        String responseBody = result.getResponse().getContentAsString();
        GenerateFollowupQuestionsResponse response = objectMapper.readValue(
                responseBody, GenerateFollowupQuestionsResponse.class);

        assertThat(response.applicationId()).isEqualTo(applicationId);
        assertThat(response.status()).isEqualTo(ApplicationStatus.QUESTIONS_SENT);
        assertThat(response.experienceId()).isEqualTo(experienceId);
        assertThat(response.questions()).hasSize(4);

        // STAR 타입 모두 포함 검증
        var types = response.questions().stream().map(q -> q.type()).toList();
        assertThat(types).containsExactlyInAnyOrder("S", "T", "A", "R");

        // DB 검증: followup_question 4개 저장 확인
        List<FollowupQuestion> savedQuestions = followupQuestionRepository.findByExperienceIdOrderByTypeAsc(experienceId);
        assertThat(savedQuestions).hasSize(4);

        // Application 상태가 QUESTIONS_SENT로 변경되었는지 확인
        var application = applicationRepository.findById(applicationId).orElseThrow();
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.QUESTIONS_SENT);
    }

    @Test
    @DisplayName("EXPERIENCE_SELECTED 상태가 아닌 지원서에서 질문 생성 시 409 에러를 반환한다")
    void generateFollowupQuestions_withWrongStatus_shouldReturn409() throws Exception {
        // Given - 지원서 제출만 완료 (SUBMITTED 상태)
        SubmitRequest submitRequest = loadExampleRequest();
        MvcResult submitResult = mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        SubmitResponse submitResponse = objectMapper.readValue(
                submitResult.getResponse().getContentAsString(), SubmitResponse.class);
        Long applicationId = submitResponse.applicationId();

        // When & Then - SUBMITTED 상태에서 질문 생성 시도 (409 에러 예상)
        mockMvc.perform(post("/api/v1/applications/{applicationId}/generate-followup-questions", applicationId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("존재하지 않는 지원서 ID로 질문 생성 시 404 에러를 반환한다")
    void generateFollowupQuestions_withNonExistentApplication_shouldReturn404() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/applications/{applicationId}/generate-followup-questions", 99999L)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("QUESTIONS_SENT 상태에서 다시 질문 생성 시 409 에러를 반환한다")
    void generateFollowupQuestions_whenAlreadyQuestionsSent_shouldReturn409() throws Exception {
        // Given - 지원서 제출 → 경험 선택 → 질문 생성까지 완료
        long experienceId = submitAndSelectExperience();
        Long applicationId = experienceRepository.findById(experienceId).orElseThrow().getApplicationId();

        // Mock STAR 질문 생성 응답
        var mockQuestions = List.of(
                new FollowupQuestion(experienceId, QuestionType.S, "S 질문"),
                new FollowupQuestion(experienceId, QuestionType.T, "T 질문"),
                new FollowupQuestion(experienceId, QuestionType.A, "A 질문"),
                new FollowupQuestion(experienceId, QuestionType.R, "R 질문")
        );
        Mockito.when(followupQuestionGenerationService.generateFollowupQuestions(
                        Mockito.eq(experienceId), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(mockQuestions);

        // 첫 번째 질문 생성 (성공)
        mockMvc.perform(post("/api/v1/applications/{applicationId}/generate-followup-questions", applicationId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // When & Then - 두 번째 질문 생성 시도 (QUESTIONS_SENT 상태에서 409 에러 예상)
        mockMvc.perform(post("/api/v1/applications/{applicationId}/generate-followup-questions", applicationId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict());
    }

    // =========================================================
    // PR4: STAR 답변 제출 + 면접 추천 질문 생성 테스트
    // =========================================================

    /**
     * 지원서 제출 → 경험 선택 → 질문 생성까지 수행하고 (applicationId, questionIds) 를 반환하는 헬퍼
     */
    private record AppWithQuestions(long applicationId, List<Long> questionIds) {}

    private AppWithQuestions submitSelectAndGenerateQuestions() throws Exception {
        // 지원서 제출
        SubmitRequest submitRequest = loadExampleRequest();
        MvcResult submitResult = mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        SubmitResponse submitResponse = objectMapper.readValue(
                submitResult.getResponse().getContentAsString(), SubmitResponse.class);
        Long applicationId = submitResponse.applicationId();

        // Mock 경험 추출
        var mockExperiences = List.of(
                Experience.createCandidate(applicationId, "브랜드 론칭 및 매출 신장", 946, 1290, 0.85)
        );
        Mockito.when(experienceExtractionService.extractExperiences(Mockito.eq(applicationId), Mockito.anyString()))
                .thenReturn(mockExperiences);

        // 경험 선택
        MvcResult selectResult = mockMvc.perform(post("/api/v1/applications/{applicationId}/select-experience", applicationId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        SelectExperienceResponse selectResponse = objectMapper.readValue(
                selectResult.getResponse().getContentAsString(), SelectExperienceResponse.class);
        long experienceId = selectResponse.selectedExperience().experienceId();

        // Mock 질문 생성
        var mockQuestions = List.of(
                new FollowupQuestion(experienceId, QuestionType.S, "당시 팀 규모와 본인 역할을 구체적으로 적어주세요."),
                new FollowupQuestion(experienceId, QuestionType.T, "해결하려던 문제와 성공 기준을 1개로 적어주세요."),
                new FollowupQuestion(experienceId, QuestionType.A, "본인이 수행한 행동을 3단계로 적어주세요."),
                new FollowupQuestion(experienceId, QuestionType.R, "결과(전후 변화)와 근거 위치를 적어주세요.")
        );
        Mockito.when(followupQuestionGenerationService.generateFollowupQuestions(
                        Mockito.eq(experienceId), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(mockQuestions);

        // 질문 생성
        MvcResult questionsResult = mockMvc.perform(post("/api/v1/applications/{applicationId}/generate-followup-questions", applicationId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        GenerateFollowupQuestionsResponse questionsResponse = objectMapper.readValue(
                questionsResult.getResponse().getContentAsString(), GenerateFollowupQuestionsResponse.class);

        List<Long> questionIds = questionsResponse.questions().stream()
                .map(q -> q.questionId())
                .toList();

        return new AppWithQuestions(applicationId, questionIds);
    }

    @Test
    @DisplayName("답변 제출 API가 정상적으로 작동한다 - 답변 저장 + 추천 JSON + status=REVIEW_READY")
    void submitFollowupAnswers_shouldReturn200WithReviewReady() throws Exception {
        // Given - 지원서 제출 → 경험 선택 → 질문 생성 완료
        AppWithQuestions setup = submitSelectAndGenerateQuestions();
        long applicationId = setup.applicationId();
        List<Long> questionIds = setup.questionIds();

        // Mock 면접 추천 질문 생성 응답
        var mockRecommendations = List.of(
                "성과 측정에 사용한 운영 로그 문서는 어떤 형태였고, 어떤 기준으로 집계했나요?",
                "요구사항 정리 단계에서 가장 중요하게 반영한 제약 조건은 무엇이었나요?",
                "DB 설계에서 가장 고민했던 테이블/인덱스 설계 선택과 그 이유를 설명해 주세요."
        );
        Mockito.when(interviewRecommendationService.generateInterviewRecommendations(
                        Mockito.eq(applicationId),
                        Mockito.anyString(),
                        Mockito.anyList(),
                        Mockito.anyList()))
                .thenReturn(mockRecommendations);

        // 답변 요청 생성 (4개 질문에 대한 답변)
        var answers = List.of(
                new AnswerItem(questionIds.get(0), "5명 팀에서 백엔드/DB 설계를 담당했습니다."),
                new AnswerItem(questionIds.get(1), "지원서 처리 시간을 30% 줄이는 것이 목표였습니다."),
                new AnswerItem(questionIds.get(2), "요구사항 정리→DB 설계→API 구현 순으로 진행했습니다."),
                new AnswerItem(questionIds.get(3), "처리 시간 10분→7분, 근거: 운영 로그 문서")
        );
        FollowupAnswersRequest request = new FollowupAnswersRequest(
                LocalDateTime.of(2026, 1, 19, 12, 10, 0),
                LocalDateTime.of(2026, 1, 19, 12, 16, 0),
                answers
        );

        // When - 답변 제출 API 호출
        MvcResult result = mockMvc.perform(post("/api/v1/applications/{applicationId}/followup-answers", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationId").value(applicationId))
                .andExpect(jsonPath("$.status").value("REVIEW_READY"))
                .andExpect(jsonPath("$.message").exists())
                .andReturn();

        // Then - 응답 검증
        String responseBody = result.getResponse().getContentAsString();
        FollowupAnswersResponse response = objectMapper.readValue(responseBody, FollowupAnswersResponse.class);

        assertThat(response.applicationId()).isEqualTo(applicationId);
        assertThat(response.status()).isEqualTo(ApplicationStatus.REVIEW_READY);
        assertThat(response.message()).isEqualTo("Answers saved. Review package is ready.");

        // DB 검증: followup_answer 4개 저장 확인
        List<FollowupAnswer> savedAnswers = followupAnswerRepository.findByQuestionIdIn(questionIds);
        assertThat(savedAnswers).hasSize(4);

        // DB 검증: 각 답변의 answerText 확인
        var answerTexts = savedAnswers.stream().map(FollowupAnswer::getAnswerText).toList();
        assertThat(answerTexts).containsExactlyInAnyOrder(
                "5명 팀에서 백엔드/DB 설계를 담당했습니다.",
                "지원서 처리 시간을 30% 줄이는 것이 목표였습니다.",
                "요구사항 정리→DB 설계→API 구현 순으로 진행했습니다.",
                "처리 시간 10분→7분, 근거: 운영 로그 문서"
        );

        // DB 검증: interview_recommendations_json 저장 확인
        var application = applicationRepository.findById(applicationId).orElseThrow();
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.REVIEW_READY);
        assertThat(application.getInterviewRecommendationsJson()).isNotNull();
        assertThat(application.getInterviewRecommendationsJson()).contains("운영 로그 문서");
    }

    @Test
    @DisplayName("QUESTIONS_SENT 상태가 아닌 지원서에서 답변 제출 시 409 에러를 반환한다")
    void submitFollowupAnswers_withWrongStatus_shouldReturn409() throws Exception {
        // Given - 지원서 제출만 완료 (SUBMITTED 상태)
        SubmitRequest submitRequest = loadExampleRequest();
        MvcResult submitResult = mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        SubmitResponse submitResponse = objectMapper.readValue(
                submitResult.getResponse().getContentAsString(), SubmitResponse.class);
        Long applicationId = submitResponse.applicationId();

        // 답변 요청 생성
        var answers = List.of(new AnswerItem(1L, "답변 텍스트"));
        FollowupAnswersRequest request = new FollowupAnswersRequest(
                LocalDateTime.now().minusMinutes(10),
                LocalDateTime.now(),
                answers
        );

        // When & Then - SUBMITTED 상태에서 답변 제출 시도 (409 에러 예상)
        mockMvc.perform(post("/api/v1/applications/{applicationId}/followup-answers", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("존재하지 않는 지원서 ID로 답변 제출 시 404 에러를 반환한다")
    void submitFollowupAnswers_withNonExistentApplication_shouldReturn404() throws Exception {
        // Given
        var answers = List.of(new AnswerItem(1L, "답변 텍스트"));
        FollowupAnswersRequest request = new FollowupAnswersRequest(
                LocalDateTime.now().minusMinutes(10),
                LocalDateTime.now(),
                answers
        );

        // When & Then
        mockMvc.perform(post("/api/v1/applications/{applicationId}/followup-answers", 99999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("answers 필드가 비어있으면 400 에러를 반환한다")
    void submitFollowupAnswers_withEmptyAnswers_shouldReturn400() throws Exception {
        // Given
        FollowupAnswersRequest request = new FollowupAnswersRequest(
                LocalDateTime.now().minusMinutes(10),
                LocalDateTime.now(),
                List.of()
        );

        // When & Then
        mockMvc.perform(post("/api/v1/applications/{applicationId}/followup-answers", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("REVIEW_READY 상태에서 다시 답변 제출 시 409 에러를 반환한다")
    void submitFollowupAnswers_whenAlreadyReviewReady_shouldReturn409() throws Exception {
        // Given - 전체 플로우 완료 (REVIEW_READY 상태)
        AppWithQuestions setup = submitSelectAndGenerateQuestions();
        long applicationId = setup.applicationId();
        List<Long> questionIds = setup.questionIds();

        Mockito.when(interviewRecommendationService.generateInterviewRecommendations(
                        Mockito.eq(applicationId), Mockito.anyString(), Mockito.anyList(), Mockito.anyList()))
                .thenReturn(List.of("추천 질문 1", "추천 질문 2", "추천 질문 3"));

        var answers = List.of(
                new AnswerItem(questionIds.get(0), "답변 S"),
                new AnswerItem(questionIds.get(1), "답변 T"),
                new AnswerItem(questionIds.get(2), "답변 A"),
                new AnswerItem(questionIds.get(3), "답변 R")
        );
        FollowupAnswersRequest request = new FollowupAnswersRequest(
                LocalDateTime.now().minusMinutes(10),
                LocalDateTime.now(),
                answers
        );

        // 첫 번째 제출 (성공)
        mockMvc.perform(post("/api/v1/applications/{applicationId}/followup-answers", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // When & Then - 두 번째 제출 시도 (REVIEW_READY 상태에서 409 에러 예상)
        mockMvc.perform(post("/api/v1/applications/{applicationId}/followup-answers", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    // =========================================================
    // PR5: 지원서 목록 조회 (평가자 큐) 테스트
    // =========================================================

    /**
     * 전체 플로우(제출→경험선택→질문생성→답변제출)를 수행해 REVIEW_READY 상태의
     * applicationId를 반환하는 헬퍼 메서드
     */
    private long createReviewReadyApplication() throws Exception {
        AppWithQuestions setup = submitSelectAndGenerateQuestions();
        long applicationId = setup.applicationId();
        List<Long> questionIds = setup.questionIds();

        Mockito.when(interviewRecommendationService.generateInterviewRecommendations(
                        Mockito.eq(applicationId), Mockito.anyString(), Mockito.anyList(), Mockito.anyList()))
                .thenReturn(List.of("추천 질문 1", "추천 질문 2", "추천 질문 3"));

        var answers = List.of(
                new AnswerItem(questionIds.get(0), "답변 S"),
                new AnswerItem(questionIds.get(1), "답변 T"),
                new AnswerItem(questionIds.get(2), "답변 A"),
                new AnswerItem(questionIds.get(3), "답변 R")
        );
        FollowupAnswersRequest request = new FollowupAnswersRequest(
                LocalDateTime.now().minusMinutes(10),
                LocalDateTime.now(),
                answers
        );

        mockMvc.perform(post("/api/v1/applications/{applicationId}/followup-answers", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        return applicationId;
    }

    @Test
    @DisplayName("REVIEW_READY 상태 필터 조회 - items 반환 및 status 필드 검증")
    void getApplicationList_withReviewReadyStatus_shouldReturnItems() throws Exception {
        // Given - REVIEW_READY 상태 지원서 1개 생성
        long applicationId = createReviewReadyApplication();

        // When & Then
        MvcResult result = mockMvc.perform(get("/api/v1/applications")
                        .param("status", "REVIEW_READY")
                        .param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].applicationId").exists())
                .andExpect(jsonPath("$.items[0].applicantId").exists())
                .andExpect(jsonPath("$.items[0].status").value("REVIEW_READY"))
                .andExpect(jsonPath("$.items[0].createdAt").exists())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        ApplicationListResponse response = objectMapper.readValue(responseBody, ApplicationListResponse.class);

        // items 중 방금 생성한 applicationId가 포함되어 있는지 확인
        assertThat(response.items()).isNotEmpty();
        boolean found = response.items().stream()
                .anyMatch(item -> item.applicationId().equals(applicationId));
        assertThat(found).isTrue();

        // 모든 items의 status가 REVIEW_READY인지 확인
        response.items().forEach(item ->
                assertThat(item.status()).isEqualTo(ApplicationStatus.REVIEW_READY));
    }

    @Test
    @DisplayName("limit 파라미터 동작 검증 - limit=1 이면 nextCursor 반환")
    void getApplicationList_withLimitOne_shouldReturnNextCursor() throws Exception {
        // Given - REVIEW_READY 상태 지원서 2개 생성
        createReviewReadyApplication();
        createReviewReadyApplication();

        // When - limit=1로 조회
        MvcResult result = mockMvc.perform(get("/api/v1/applications")
                        .param("status", "REVIEW_READY")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        ApplicationListResponse response = objectMapper.readValue(responseBody, ApplicationListResponse.class);

        // 2개 이상 존재하므로 nextCursor가 null이 아니어야 함
        assertThat(response.nextCursor()).isNotNull();
    }

    @Test
    @DisplayName("cursor 파라미터로 다음 페이지 조회 - 커서 기반 페이지네이션")
    void getApplicationList_withCursor_shouldReturnNextPage() throws Exception {
        // Given - REVIEW_READY 상태 지원서 2개 생성
        createReviewReadyApplication();
        createReviewReadyApplication();

        // 첫 페이지: limit=1
        MvcResult firstPageResult = mockMvc.perform(get("/api/v1/applications")
                        .param("status", "REVIEW_READY")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andReturn();

        ApplicationListResponse firstPage = objectMapper.readValue(
                firstPageResult.getResponse().getContentAsString(), ApplicationListResponse.class);

        assertThat(firstPage.items()).hasSize(1);
        assertThat(firstPage.nextCursor()).isNotNull();

        long firstId = firstPage.items().get(0).applicationId();

        // 두 번째 페이지: cursor 사용
        MvcResult secondPageResult = mockMvc.perform(get("/api/v1/applications")
                        .param("status", "REVIEW_READY")
                        .param("limit", "1")
                        .param("cursor", firstPage.nextCursor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andReturn();

        ApplicationListResponse secondPage = objectMapper.readValue(
                secondPageResult.getResponse().getContentAsString(), ApplicationListResponse.class);

        // 두 번째 페이지의 applicationId는 첫 번째와 달라야 함 (id > firstId)
        long secondId = secondPage.items().get(0).applicationId();
        assertThat(secondId).isGreaterThan(firstId);
    }

    @Test
    @DisplayName("SUBMITTED 상태 필터 조회 - REVIEW_READY 항목이 포함되지 않는다")
    void getApplicationList_withSubmittedStatus_shouldNotContainReviewReady() throws Exception {
        // Given - REVIEW_READY 1개, SUBMITTED 1개 생성
        createReviewReadyApplication();

        SubmitRequest submitRequest = loadExampleRequest();
        mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitRequest)))
                .andExpect(status().isCreated());

        // When - SUBMITTED 상태 필터로 조회
        MvcResult result = mockMvc.perform(get("/api/v1/applications")
                        .param("status", "SUBMITTED")
                        .param("limit", "50"))
                .andExpect(status().isOk())
                .andReturn();

        ApplicationListResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), ApplicationListResponse.class);

        // 모든 items의 status가 SUBMITTED인지 확인
        assertThat(response.items()).isNotEmpty();
        response.items().forEach(item ->
                assertThat(item.status()).isEqualTo(ApplicationStatus.SUBMITTED));
    }

    @Test
    @DisplayName("마지막 페이지에서 nextCursor가 null이다")
    void getApplicationList_lastPage_shouldReturnNullNextCursor() throws Exception {
        // Given - REVIEW_READY 1개 생성
        createReviewReadyApplication();

        // When - 충분히 큰 limit으로 조회
        MvcResult result = mockMvc.perform(get("/api/v1/applications")
                        .param("status", "REVIEW_READY")
                        .param("limit", "100"))
                .andExpect(status().isOk())
                .andReturn();

        ApplicationListResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), ApplicationListResponse.class);

        // 모든 데이터를 가져왔으므로 nextCursor는 null이어야 함
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    @DisplayName("limit이 범위를 벗어나면 400 에러를 반환한다")
    void getApplicationList_withInvalidLimit_shouldReturn400() throws Exception {
        // limit=0 (범위 초과)
        mockMvc.perform(get("/api/v1/applications")
                        .param("status", "REVIEW_READY")
                        .param("limit", "0"))
                .andExpect(status().isBadRequest());

        // limit=101 (범위 초과)
        mockMvc.perform(get("/api/v1/applications")
                        .param("status", "REVIEW_READY")
                        .param("limit", "101"))
                .andExpect(status().isBadRequest());
    }
}
