package com.khuda.khuda_clue_api.controller;

import com.khuda.khuda_clue_api.domain.ApplicationStatus;
import com.khuda.khuda_clue_api.dto.request.FollowupAnswersRequest;
import com.khuda.khuda_clue_api.dto.request.SubmitRequest;
import com.khuda.khuda_clue_api.dto.response.ApplicationListResponse;
import com.khuda.khuda_clue_api.dto.response.FollowupAnswersResponse;
import com.khuda.khuda_clue_api.dto.response.GenerateFollowupQuestionsResponse;
import com.khuda.khuda_clue_api.dto.response.RecommendInterviewQuestionsResponse;
import com.khuda.khuda_clue_api.dto.response.ReviewDetailResponse;
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

    /**
     * 지원서 목록 조회 (평가자 큐)
     * GET /api/v1/applications?status=REVIEW_READY&limit=50&cursor=...
     */
    @GetMapping
    public ResponseEntity<ApplicationListResponse> getApplicationList(
            @RequestParam(defaultValue = "REVIEW_READY") ApplicationStatus status,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String cursor
    ) {
        ApplicationListResponse response = applicationService.getApplicationList(status, limit, cursor);
        return ResponseEntity.ok(response);
    }

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

    /**
     * 평가자 결과 조회 (한 화면 완성 패키지)
     * GET /api/v1/applications/{applicationId}/review
     */
    @GetMapping("/{applicationId}/review")
    public ResponseEntity<ReviewDetailResponse> getReviewDetail(@PathVariable Long applicationId) {
        ReviewDetailResponse response = applicationService.getReviewDetail(applicationId);
        return ResponseEntity.ok(response);
    }

    /**
     * 면접 추천 질문 재생성
     * POST /api/v1/applications/{applicationId}/recommend-interview-questions
     */
    @PostMapping("/{applicationId}/recommend-interview-questions")
    public ResponseEntity<RecommendInterviewQuestionsResponse> recommendInterviewQuestions(
            @PathVariable Long applicationId
    ) {
        RecommendInterviewQuestionsResponse response = applicationService.recommendInterviewQuestions(applicationId);
        return ResponseEntity.ok(response);
    }
}
