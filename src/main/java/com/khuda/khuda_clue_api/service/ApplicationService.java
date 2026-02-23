package com.khuda.khuda_clue_api.service;

import com.khuda.khuda_clue_api.domain.ApplicationStatus;
import com.khuda.khuda_clue_api.dto.request.SubmitRequest;
import com.khuda.khuda_clue_api.dto.response.GenerateFollowupQuestionsResponse;
import com.khuda.khuda_clue_api.dto.response.QuestionDto;
import com.khuda.khuda_clue_api.dto.response.SelectExperienceResponse;
import com.khuda.khuda_clue_api.dto.response.SelectedExperience;
import com.khuda.khuda_clue_api.dto.response.SubmitResponse;
import com.khuda.khuda_clue_api.entity.Application;
import com.khuda.khuda_clue_api.entity.Experience;
import com.khuda.khuda_clue_api.entity.FollowupQuestion;
import com.khuda.khuda_clue_api.repository.ApplicationRepository;
import com.khuda.khuda_clue_api.repository.ExperienceRepository;
import com.khuda.khuda_clue_api.repository.FollowupQuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ExperienceRepository experienceRepository;
    private final FollowupQuestionRepository followupQuestionRepository;
    private final ExperienceExtractionService experienceExtractionService;
    private final FollowupQuestionGenerationService followupQuestionGenerationService;

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
}
