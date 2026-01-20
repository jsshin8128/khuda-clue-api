package com.khuda.khuda_clue_api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.khuda.khuda_clue_api.domain.ApplicationStatus;
import com.khuda.khuda_clue_api.dto.request.SubmitRequest;
import com.khuda.khuda_clue_api.dto.response.SubmitResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;

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

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @DisplayName("지원서 제출 API가 정상적으로 작동한다")
    void submitApplication_shouldReturn201WithValidResponse() throws Exception {
        // Given
        SubmitRequest request = new SubmitRequest("test-applicant-123", "이것은 테스트 자기소개서입니다.");

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
}