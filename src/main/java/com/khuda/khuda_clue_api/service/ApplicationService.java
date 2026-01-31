package com.khuda.khuda_clue_api.service;

import com.khuda.khuda_clue_api.domain.ApplicationStatus;
import com.khuda.khuda_clue_api.dto.request.SubmitRequest;
import com.khuda.khuda_clue_api.dto.response.SelectExperienceResponse;
import com.khuda.khuda_clue_api.dto.response.SelectedExperience;
import com.khuda.khuda_clue_api.dto.response.SubmitResponse;
import com.khuda.khuda_clue_api.entity.Application;
import com.khuda.khuda_clue_api.entity.Experience;
import com.khuda.khuda_clue_api.repository.ApplicationRepository;
import com.khuda.khuda_clue_api.repository.ExperienceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ExperienceRepository experienceRepository;
    private final ExperienceExtractionService experienceExtractionService;

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

}