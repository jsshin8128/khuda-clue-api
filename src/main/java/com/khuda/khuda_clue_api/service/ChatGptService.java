package com.khuda.khuda_clue_api.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khuda.khuda_clue_api.config.ChatGptProperties;
import com.khuda.khuda_clue_api.entity.Experience;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@org.springframework.context.annotation.Primary
public class ChatGptService implements ExperienceExtractionService {

    private static final String GPT_MODEL = "gpt-4o-mini";
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";

    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final HttpClient httpClient;

    public ChatGptService(ChatGptProperties chatGptProperties) {
        this.objectMapper = new ObjectMapper();
        this.apiKey = chatGptProperties.getApiKey();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        
        if (apiKey == null || apiKey.isEmpty()) {
            log.error("ChatGPT API key is not set. Please set CHATGPT_API_KEY environment variable or chatgpt.api.key property.");
        } else {
            log.info("ChatGPT API key is configured. Key length: {}", apiKey.length());
        }
    }

    @Override
    public List<Experience> extractExperiences(Long applicationId, String coverLetterText) {
        log.info("Extracting experiences using ChatGPT for applicationId: {}", applicationId);

        try {
            String prompt = buildPrompt(coverLetterText);
            ChatCompletionRequest request = new ChatCompletionRequest(
                    GPT_MODEL,
                    List.of(new Message("user", prompt)),
                    1.0,
                    2000
            );

            String requestBody = objectMapper.writeValueAsString(request);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            log.debug("Sending request to ChatGPT API. URL: {}, Model: {}", API_URL, GPT_MODEL);
            HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            String responseBody = httpResponse.body();

            log.debug("ChatGPT API response status: {}, body length: {}", httpResponse.statusCode(), responseBody != null ? responseBody.length() : 0);
            log.debug("ChatGPT API response body: {}", responseBody);

            if (httpResponse.statusCode() != 200) {
                log.error("ChatGPT API returned error status: {}, body: {}", httpResponse.statusCode(), responseBody);
                return new ArrayList<>();
            }

            ChatCompletionResponse response = objectMapper.readValue(responseBody, ChatCompletionResponse.class);

            if (response == null || response.choices == null || response.choices.isEmpty()) {
                log.warn("No response from ChatGPT API");
                return new ArrayList<>();
            }

            String content = response.choices.get(0).message.content;
            log.info("ChatGPT response received: {}", content);

            List<Experience> experiences = parseExperiences(applicationId, coverLetterText, content);
            
            // rankScore 내림차순 정렬
            experiences.sort(Comparator.comparing(Experience::getRankScore).reversed());
            
            // 설계 의도: 검증에 가장 유리한 1개의 경험만 선택하여 반환
            return experiences.stream()
                    .limit(1)
                    .toList();

        } catch (Exception e) {
            log.error("Error during experience extraction with ChatGPT. Exception type: {}, message: {}", 
                    e.getClass().getSimpleName(), e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    private String buildPrompt(String coverLetterText) {
        return """
            다음은 지원자의 자기소개서입니다. 이 자소서에서 '경험'을 의미적으로 발견하고, 각 경험이 STAR 기법(Situation-Task-Action-Result) 관점에서 얼마나 완성도가 높은지 평가해주세요.
            
            자기소개서:
            %s
            
            요구사항:
            1. 자소서 전체 맥락에서 '경험의 경계'를 의미적으로 발견하세요. 단순 문장 분류가 아니라, 여러 요소가 섞인 텍스트에서 경험만 골라내세요.
            2. 각 경험에 대해 Goal(목표)-Action(행동)-Result(결과)의 완성도를 종합적으로 평가하세요.
            3. 각 경험을 다음 JSON 형식으로 반환하세요:
            
            [
              {
                "title": "경험의 제목 또는 요약 (자기소개서 원문에서 추출한 텍스트)",
                "startIdx": 시작_인덱스_숫자,
                "endIdx": 끝_인덱스_숫자,
                "rankScore": 0.0부터_1.0까지의_점수
              }
            ]
            
            주의사항:
            - startIdx와 endIdx는 자소서 원문에서 해당 경험이 시작하고 끝나는 문자 위치입니다.
            - rankScore는 STAR 완성도, 구체성, 성과의 명확성을 종합한 점수입니다 (0.0 ~ 1.0).
            - 최대 3개의 경험만 반환하세요.
            - JSON 형식만 반환하고, 다른 설명은 포함하지 마세요.
            """.formatted(coverLetterText);
    }

    private List<Experience> parseExperiences(Long applicationId, String coverLetterText, String content) {
        List<Experience> experiences = new ArrayList<>();

        try {
            // JSON 배열 추출 (마크다운 코드 블록 제거)
            String jsonContent = content.trim();
            if (jsonContent.startsWith("```json")) {
                jsonContent = jsonContent.substring(7);
            }
            if (jsonContent.startsWith("```")) {
                jsonContent = jsonContent.substring(3);
            }
            if (jsonContent.endsWith("```")) {
                jsonContent = jsonContent.substring(0, jsonContent.length() - 3);
            }
            jsonContent = jsonContent.trim();

            // JSON 파싱
            List<ExperienceJson> experienceJsons = objectMapper.readValue(
                    jsonContent,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, ExperienceJson.class)
            );

            for (ExperienceJson expJson : experienceJsons) {
                // startIdx와 endIdx 검증 및 조정
                int startIdx = Math.max(0, Math.min(expJson.startIdx, coverLetterText.length() - 1));
                int endIdx = Math.max(startIdx + 1, Math.min(expJson.endIdx, coverLetterText.length()));
                
                // rankScore 검증
                double rankScore = Math.max(0.0, Math.min(1.0, expJson.rankScore));

                Experience experience = Experience.createCandidate(
                        applicationId,
                        expJson.title,
                        startIdx,
                        endIdx,
                        rankScore
                );
                experiences.add(experience);
                log.info("Parsed experience: title={}, startIdx={}, endIdx={}, rankScore={}",
                        expJson.title, startIdx, endIdx, rankScore);
            }

        } catch (Exception e) {
            log.error("Error parsing ChatGPT response. Parsing failed, returning empty list. Content: {}", content, e);
            return new ArrayList<>();
        }

        return experiences;
    }

    @Data
    private static class ChatCompletionRequest {
        private String model;
        private List<Message> messages;
        private double temperature;
        @JsonProperty("max_tokens")
        private int maxTokens;

        public ChatCompletionRequest(String model, List<Message> messages, double temperature, int maxTokens) {
            this.model = model;
            this.messages = messages;
            this.temperature = temperature;
            this.maxTokens = maxTokens;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Message {
        private String role;
        private String content;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ChatCompletionResponse {
        private List<Choice> choices;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Choice {
        private Message message;
    }

    @Data
    private static class ExperienceJson {
        private String title;
        private int startIdx;
        private int endIdx;
        private double rankScore;
    }
}
