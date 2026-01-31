package com.khuda.khuda_clue_api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.khuda.khuda_clue_api.domain.ApplicationStatus;
import com.khuda.khuda_clue_api.dto.request.SubmitRequest;
import com.khuda.khuda_clue_api.dto.response.SelectExperienceResponse;
import com.khuda.khuda_clue_api.dto.response.SubmitResponse;
import com.khuda.khuda_clue_api.entity.Experience;
import com.khuda.khuda_clue_api.repository.ApplicationRepository;
import com.khuda.khuda_clue_api.repository.ExperienceRepository;
import com.khuda.khuda_clue_api.service.ExperienceExtractionService;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
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
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private ExperienceRepository experienceRepository;

    @MockitoBean
    private ExperienceExtractionService experienceExtractionService;

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
}