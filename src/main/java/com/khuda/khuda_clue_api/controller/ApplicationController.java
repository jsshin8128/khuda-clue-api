package com.khuda.khuda_clue_api.controller;

import com.khuda.khuda_clue_api.dto.request.FollowupAnswersRequest;
import com.khuda.khuda_clue_api.dto.request.SubmitRequest;
import com.khuda.khuda_clue_api.dto.response.FollowupAnswersResponse;
import com.khuda.khuda_clue_api.dto.response.GenerateFollowupQuestionsResponse;
import com.khuda.khuda_clue_api.dto.response.SelectExperienceResponse;
import com.khuda.khuda_clue_api.dto.response.SubmitResponse;
import com.khuda.khuda_clue_api.service.ApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    @PostMapping
    public ResponseEntity<SubmitResponse> submitApplication(@Valid @RequestBody SubmitRequest request) {
        SubmitResponse response = applicationService.createApplication(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{applicationId}/select-experience")
    public ResponseEntity<SelectExperienceResponse> selectExperience(@PathVariable Long applicationId) {
        SelectExperienceResponse response = applicationService.selectExperience(applicationId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{applicationId}/generate-followup-questions")
    public ResponseEntity<GenerateFollowupQuestionsResponse> generateFollowupQuestions(@PathVariable Long applicationId) {
        GenerateFollowupQuestionsResponse response = applicationService.generateFollowupQuestions(applicationId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{applicationId}/followup-answers")
    public ResponseEntity<FollowupAnswersResponse> submitFollowupAnswers(
            @PathVariable Long applicationId,
            @Valid @RequestBody FollowupAnswersRequest request
    ) {
        FollowupAnswersResponse response = applicationService.submitFollowupAnswers(applicationId, request);
        return ResponseEntity.ok(response);
    }
}
