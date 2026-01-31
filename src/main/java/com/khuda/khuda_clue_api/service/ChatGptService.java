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

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
    private static final String EXAMPLE_COVER_LETTER_RESOURCE_PATH = "/prompt/experience-extraction-example-coverletter.txt";

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
            String systemPrompt = buildSystemPrompt();
            String userPrompt = buildPrompt(coverLetterText);
            ChatCompletionRequest request = new ChatCompletionRequest(
                    GPT_MODEL,
                    List.of(
                            new Message("system", systemPrompt),
                            new Message("user", userPrompt)
                    ),
                    0.2,
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
        String oneShotExample = buildOneShotExampleSection();
        return """
            [과제 설명]
            너는 채용 평가 보조 AI다. 입력으로 주어지는 자기소개서 전체 텍스트에서 “검증 가치가 높은 경험” 구간을 의미적으로 찾아내고, 각 경험의 STAR 완성도(상황/과제/행동/결과), 구체성, 성과의 명확성을 종합해 점수를 매겨라.
            
            - 경험이란: 지원자가 직접 수행한 행동이 있고, 그로 인한 결과/성과(정량 또는 정성)가 드러나는 “연속 텍스트 구간”이다.
            - 경험 경계는 문단/문장 기준이 아니라 의미 기준으로 잡아라. 단, 반드시 원문에서 연속된 substring이어야 한다.
            - 인덱스 기준: 0-based 문자 인덱스이며, endIdx는 경험 구간의 마지막 문자 “다음” 위치(즉, Java String substring(startIdx, endIdx)로 정확히 잘라지는 범위)다.
            - 출력은 반드시 JSON 배열 1개만. 코드펜스(```), 설명 문장, 마크다운, 주석을 절대 포함하지 마라.
            
            %s
            
            [과제]
            아래 [입력 자소서]에서 경험을 최대 3개까지 찾아 JSON 배열로 반환하라.
            - rankScore는 0.0~1.0
            - rankScore 내림차순으로 정렬
            - title은 원문에서 그대로 발췌한 짧은 구절(경험을 대표하는 문장/절)로 작성
            - 아래 [출력 JSON 스키마]는 “형식 참고용”이다. 스키마 예시를 그대로 복사하지 말고, 반드시 실제 값으로 채워 출력해라.
            
            [입력 자소서]
            %s
            
            [출력 JSON 스키마]
            [
              {
                "title": "원문 발췌",
                "startIdx": 0,
                "endIdx": 0,
                "rankScore": 0.0
              }
            ]
            """.formatted(oneShotExample, coverLetterText);
    }

    private String buildSystemPrompt() {
        return """
            너는 JSON만 출력하는 정보 추출기다.
            사용자의 입력 텍스트에서 경험 구간을 찾아내고, 지정된 스키마의 JSON 배열만 반환해라.
            절대 설명을 붙이지 마라.
            """;
    }

    private String buildOneShotExampleSection() {
        String exampleCoverLetter = loadExampleCoverLetterFromResources();
        if (exampleCoverLetter == null || exampleCoverLetter.isBlank()) {
            return "";
        }

        ExampleExperience e1 = ExampleExperience.fromBoundaries(
                "개인브랜드 운영 및 트렌드 분석",
                exampleCoverLetter,
                "1인 브랜드 운영자였던 만큼",
                "극대화하겠습니다."
        );
        ExampleExperience e2 = ExampleExperience.fromBoundaries(
                "콘텐츠팀 인턴 SNS 콘텐츠 제작",
                exampleCoverLetter,
                "콘텐츠팀 인턴으로 근무하며 신규 사업체의 SNS 콘텐츠를 제작했던 경험이 있습니다.",
                "SNS 콘텐츠를 제작했습니다."
        );
        ExampleExperience e3 = ExampleExperience.fromBoundaries(
                "브랜드 론칭 및 매출 신장",
                exampleCoverLetter,
                "인턴십 종료 후에는 직접 촬영한 사진을 기반으로 하는 브랜드 론칭에 도전했습니다.",
                "매출을 신장할 수 있었습니다."
        );

        // 예시는 “형식 + 인덱스 기준”을 보여주기 위한 1-shot 이므로, 점수는 상대 비교로만 제시
        return """
            [예시 - 1 shot]
            아래는 “입력 자소서 → 출력 JSON” 예시다. (형식/인덱스 기준을 그대로 따라라.)
            
            [예시 입력 자소서]
            %s
            
            [예시 출력 JSON]
            [
              {
                "title": "%s",
                "startIdx": %d,
                "endIdx": %d,
                "rankScore": 0.65
              },
              {
                "title": "%s",
                "startIdx": %d,
                "endIdx": %d,
                "rankScore": 0.75
              },
              {
                "title": "%s",
                "startIdx": %d,
                "endIdx": %d,
                "rankScore": 0.85
              }
            ]
            """.formatted(
                exampleCoverLetter,
                e1.title, e1.startIdx, e1.endIdx,
                e2.title, e2.startIdx, e2.endIdx,
                e3.title, e3.startIdx, e3.endIdx
        );
    }

    private String loadExampleCoverLetterFromResources() {
        try (InputStream is = ChatGptService.class.getResourceAsStream(EXAMPLE_COVER_LETTER_RESOURCE_PATH)) {
            if (is == null) {
                log.warn("One-shot example cover letter resource not found: {}", EXAMPLE_COVER_LETTER_RESOURCE_PATH);
                return null;
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Failed to load one-shot example cover letter from resources. path={}", EXAMPLE_COVER_LETTER_RESOURCE_PATH, e);
            return null;
        }
    }

    private record ExampleExperience(String title, int startIdx, int endIdx) {
        static ExampleExperience fromBoundaries(String title, String fullText, String startAnchor, String endAnchor) {
            int start = fullText.indexOf(startAnchor);
            if (start < 0) {
                throw new IllegalStateException("Example startAnchor not found: " + startAnchor);
            }
            int endAnchorIdx = fullText.indexOf(endAnchor, start);
            if (endAnchorIdx < 0) {
                throw new IllegalStateException("Example endAnchor not found after startAnchor: " + endAnchor);
            }
            int endExclusive = endAnchorIdx + endAnchor.length();
            return new ExampleExperience(title, start, endExclusive);
        }
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
