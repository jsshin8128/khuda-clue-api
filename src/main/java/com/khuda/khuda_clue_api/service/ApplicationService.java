package com.khuda.khuda_clue_api.service;

import com.khuda.khuda_clue_api.dto.request.SubmitRequest;
import com.khuda.khuda_clue_api.dto.response.SubmitResponse;
import com.khuda.khuda_clue_api.entity.Application;
import com.khuda.khuda_clue_api.repository.ApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationService {

    private final ApplicationRepository applicationRepository;

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
}