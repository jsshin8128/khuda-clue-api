package com.khuda.khuda_clue_api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.khuda.khuda_clue_api.domain.ApplicationStatus;
import com.khuda.khuda_clue_api.dto.request.AnswerItem;
import com.khuda.khuda_clue_api.dto.request.FollowupAnswersRequest;
import com.khuda.khuda_clue_api.dto.request.SubmitRequest;
import com.khuda.khuda_clue_api.dto.response.ApplicationListItemDto;
import com.khuda.khuda_clue_api.dto.response.ApplicationListResponse;
import com.khuda.khuda_clue_api.dto.response.FollowupAnswersResponse;
import com.khuda.khuda_clue_api.dto.response.FollowupItemDto;
import com.khuda.khuda_clue_api.dto.response.GenerateFollowupQuestionsResponse;
import com.khuda.khuda_clue_api.dto.response.QuestionDto;
import com.khuda.khuda_clue_api.dto.response.ReviewDetailResponse;
import com.khuda.khuda_clue_api.dto.response.ReviewSelectedExperienceDto;
import com.khuda.khuda_clue_api.dto.response.SelectExperienceResponse;
import com.khuda.khuda_clue_api.dto.response.SelectedExperience;
import com.khuda.khuda_clue_api.dto.response.SubmitResponse;
import com.khuda.khuda_clue_api.entity.Application;
import com.khuda.khuda_clue_api.entity.Experience;
import com.khuda.khuda_clue_api.entity.FollowupAnswer;
import com.khuda.khuda_clue_api.entity.FollowupQuestion;
import com.khuda.khuda_clue_api.repository.ApplicationRepository;
import com.khuda.khuda_clue_api.repository.ExperienceRepository;
import com.khuda.khuda_clue_api.repository.FollowupAnswerRepository;
import com.khuda.khuda_clue_api.repository.FollowupQuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ExperienceRepository experienceRepository;
    private final FollowupQuestionRepository followupQuestionRepository;
    private final FollowupAnswerRepository followupAnswerRepository;
    private final ExperienceExtractionService experienceExtractionService;
    private final FollowupQuestionGenerationService followupQuestionGenerationService;
    private final InterviewRecommendationService interviewRecommendationService;

    // ObjectMapper는 ChatGptService와 동일하게 직접 생성 (Spring 빈 등록 없이 사용)
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Transactional
    public SubmitResponse createApplication(SubmitRequest request) {
        Application application = new Application(request.applicantId(), request.coverLetterText());
        Application savedApplication = applicationRepository.save(application);

        return new SubmitResponse(
                savedApplication.getId(),
                savedApplication.getStatus(),
                savedApplication.getCreatedAt()
        );
    }

    @Transactional
    public SelectExperienceResponse selectExperience(Long applicationId) {
        // 상태 가드: SUBMITTED 상태만 허용
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));

        if (application.getStatus() != ApplicationStatus.SUBMITTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Experience selection is only allowed for SUBMITTED applications. Current status: " + application.getStatus());
        }

        // 자소서에서 경험 추출
        var candidates = experienceExtractionService.extractExperiences(applicationId, application.getCoverLetterText());
        experienceRepository.saveAll(candidates);

        // 경험 후보가 없으면 예외 발생
        if (candidates.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No valid experiences could be extracted from the cover letter");
        }

        // 최고 점수의 경험을 selected로 확정
        Experience selectedExperience = candidates.get(0);
        selectedExperience.markAsSelected();
        experienceRepository.save(selectedExperience);

        // 애플리케이션 상태 업데이트
        application.updateStatus(ApplicationStatus.EXPERIENCE_SELECTED);
        Application updatedApplication = applicationRepository.save(application);

        // 응답 생성
        SelectedExperience selectedExperienceDto = new SelectedExperience(
                selectedExperience.getId(),
                selectedExperience.getTitle(),
                selectedExperience.getStartIdx(),
                selectedExperience.getEndIdx(),
                selectedExperience.getRankScore()
        );

        return new SelectExperienceResponse(
                updatedApplication.getId(),
                updatedApplication.getStatus(),
                selectedExperienceDto
        );
    }

    @Transactional
    public GenerateFollowupQuestionsResponse generateFollowupQuestions(Long applicationId) {
        // 상태 가드: EXPERIENCE_SELECTED 상태만 허용
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));

        if (application.getStatus() != ApplicationStatus.EXPERIENCE_SELECTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Follow-up question generation is only allowed for EXPERIENCE_SELECTED applications. Current status: " + application.getStatus());
        }

        // 선택된 경험 조회
        Experience selectedExperience = experienceRepository
                .findByApplicationIdAndIsSelectedTrue(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "No selected experience found for applicationId: " + applicationId));

        // STAR 질문 4개 생성
        List<FollowupQuestion> generatedQuestions = followupQuestionGenerationService.generateFollowupQuestions(
                selectedExperience.getId(),
                selectedExperience.getTitle(),
                application.getCoverLetterText()
        );

        // 질문 생성 실패 시 예외 발생
        if (generatedQuestions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to generate follow-up questions for experienceId: " + selectedExperience.getId());
        }

        // 생성된 질문 저장
        List<FollowupQuestion> savedQuestions = followupQuestionRepository.saveAll(generatedQuestions);

        // 애플리케이션 상태 업데이트: EXPERIENCE_SELECTED → QUESTIONS_SENT
        application.updateStatus(ApplicationStatus.QUESTIONS_SENT);
        applicationRepository.save(application);

        // 응답 생성
        List<QuestionDto> questionDtos = savedQuestions.stream()
                .map(q -> new QuestionDto(q.getId(), q.getType().name(), q.getQuestionText()))
                .toList();

        return new GenerateFollowupQuestionsResponse(
                applicationId,
                ApplicationStatus.QUESTIONS_SENT,
                selectedExperience.getId(),
                questionDtos
        );
    }

    /**
     * 지원서 목록 조회 (커서 기반 페이지네이션)
     * - status 필터링 + id 기반 오름차순 정렬
     * - cursor: Base64 인코딩된 마지막 id (null이면 첫 페이지)
     * - limit+1 개 조회 후 초과분이 있으면 nextCursor 반환
     */
    public ApplicationListResponse getApplicationList(ApplicationStatus status, int limit, String cursor) {
        // limit 범위 검증 (1 ~ 100)
        if (limit < 1 || limit > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "limit must be between 1 and 100");
        }

        // limit+1 개 조회하여 다음 페이지 존재 여부 판단
        PageRequest pageRequest = PageRequest.of(0, limit + 1);
        List<Application> rows;

        if (cursor == null || cursor.isBlank()) {
            // 첫 페이지: 커서 없이 조회
            rows = applicationRepository.findByStatusOrderByIdAsc(status, pageRequest);
        } else {
            // 이후 페이지: cursor 디코딩 후 id > cursorId 조건으로 조회
            Long cursorId = decodeCursor(cursor);
            rows = applicationRepository.findByStatusAndIdGreaterThanOrderByIdAsc(status, cursorId, pageRequest);
        }

        // 다음 페이지 존재 여부 확인
        boolean hasNext = rows.size() > limit;
        List<Application> pageItems = hasNext ? rows.subList(0, limit) : rows;

        // 응답 DTO 변환
        List<ApplicationListItemDto> items = pageItems.stream()
                .map(a -> new ApplicationListItemDto(
                        a.getId(),
                        a.getApplicantId(),
                        a.getStatus(),
                        a.getCreatedAt()
                ))
                .toList();

        // 다음 커서 계산: 현재 페이지 마지막 항목의 id를 인코딩
        String nextCursor = hasNext ? encodeCursor(pageItems.get(pageItems.size() - 1).getId()) : null;

        return new ApplicationListResponse(items, nextCursor);
    }

    /**
     * 커서 인코딩: id → Base64 문자열
     */
    private String encodeCursor(Long id) {
        return Base64.getEncoder().encodeToString(id.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 커서 디코딩: Base64 문자열 → id
     */
    private Long decodeCursor(String cursor) {
        try {
            String decoded = new String(Base64.getDecoder().decode(cursor), StandardCharsets.UTF_8);
            return Long.parseLong(decoded);
        } catch (IllegalArgumentException e) {
            // NumberFormatException은 IllegalArgumentException의 하위 클래스이므로 여기서 함께 처리됨
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cursor value");
        }
    }

    @Transactional
    public FollowupAnswersResponse submitFollowupAnswers(Long applicationId, FollowupAnswersRequest request) {
        // 상태 가드: QUESTIONS_SENT 상태만 허용
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));

        if (application.getStatus() != ApplicationStatus.QUESTIONS_SENT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Answer submission is only allowed for QUESTIONS_SENT applications. Current status: "
                            + application.getStatus());
        }

        // 선택된 경험 조회
        Experience selectedExperience = experienceRepository
                .findByApplicationIdAndIsSelectedTrue(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "No selected experience found for applicationId: " + applicationId));

        // 해당 경험의 질문 목록 조회
        List<FollowupQuestion> questions = followupQuestionRepository
                .findByExperienceIdOrderByTypeAsc(selectedExperience.getId());

        if (questions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No followup questions found for experienceId: " + selectedExperience.getId());
        }

        // 답변 저장 (질문당 1개)
        List<FollowupAnswer> answers = request.answers().stream()
                .map((AnswerItem item) -> new FollowupAnswer(
                        item.questionId(),
                        item.answerText(),
                        request.startedAt(),
                        request.submittedAt()
                ))
                .toList();
        List<FollowupAnswer> savedAnswers = followupAnswerRepository.saveAll(answers);

        // 상태 업데이트: QUESTIONS_SENT → ANSWERED
        application.updateStatus(ApplicationStatus.ANSWERED);
        applicationRepository.save(application);

        // 면접 추천 질문 생성 (내부 로직)
        List<String> recommendations = interviewRecommendationService.generateInterviewRecommendations(
                applicationId,
                application.getCoverLetterText(),
                questions,
                savedAnswers
        );

        if (recommendations.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to generate interview recommendations for applicationId: " + applicationId);
        }

        // 추천 질문 JSON으로 직렬화 후 저장
        String recommendationsJson;
        try {
            recommendationsJson = objectMapper.writeValueAsString(recommendations);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to serialize interview recommendations: " + e.getMessage());
        }

        application.updateInterviewRecommendations(recommendationsJson);

        // 상태 업데이트: ANSWERED → REVIEW_READY
        application.updateStatus(ApplicationStatus.REVIEW_READY);
        applicationRepository.save(application);

        return new FollowupAnswersResponse(
                applicationId,
                ApplicationStatus.REVIEW_READY,
                "Answers saved. Review package is ready."
        );
    }

    /**
     * 평가자 결과 조회 (한 화면 완성 패키지)
     * - 상태 가드: REVIEW_READY 상태만 허용
     * - coverLetterText + selectedExperience + STAR 질문·답변 + 면접 추천 질문 반환
     */
    public ReviewDetailResponse getReviewDetail(Long applicationId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));

        if (application.getStatus() != ApplicationStatus.REVIEW_READY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Review detail is only available for REVIEW_READY applications. Current status: "
                            + application.getStatus());
        }

        Experience selectedExperience = experienceRepository
                .findByApplicationIdAndIsSelectedTrue(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "No selected experience found for applicationId: " + applicationId));

        List<FollowupQuestion> questions = followupQuestionRepository
                .findByExperienceIdOrderByTypeAsc(selectedExperience.getId());

        List<Long> questionIds = questions.stream().map(FollowupQuestion::getId).toList();

        Map<Long, String> answerByQuestionId = followupAnswerRepository
                .findByQuestionIdIn(questionIds)
                .stream()
                .collect(Collectors.toMap(FollowupAnswer::getQuestionId, FollowupAnswer::getAnswerText));

        List<FollowupItemDto> followupItems = questions.stream()
                .map(q -> new FollowupItemDto(
                        q.getType().name(),
                        q.getId(),
                        q.getQuestionText(),
                        answerByQuestionId.get(q.getId())
                ))
                .toList();

        List<String> recommendations = deserializeRecommendations(
                application.getInterviewRecommendationsJson(), applicationId);

        return new ReviewDetailResponse(
                application.getId(),
                application.getApplicantId(),
                application.getStatus(),
                application.getCoverLetterText(),
                new ReviewSelectedExperienceDto(
                        selectedExperience.getId(),
                        selectedExperience.getTitle(),
                        selectedExperience.getStartIdx(),
                        selectedExperience.getEndIdx()
                ),
                followupItems,
                recommendations
        );
    }

    /**
     * interview_recommendations_json 역직렬화
     */
    @SuppressWarnings("unchecked")
    private List<String> deserializeRecommendations(String json, Long applicationId) {
        if (json == null || json.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Interview recommendations not found for applicationId: " + applicationId);
        }
        try {
            return objectMapper.readValue(json, List.class);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to deserialize interview recommendations: " + e.getMessage());
        }
    }
}
