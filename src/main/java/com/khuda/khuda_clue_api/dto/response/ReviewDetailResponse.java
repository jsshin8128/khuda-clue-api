package com.khuda.khuda_clue_api.dto.response;

import com.khuda.khuda_clue_api.domain.ApplicationStatus;

import java.util.List;

/**
 * GET /applications/{id}/review 응답 — 평가자 한 화면 완성 패키지
 */
public record ReviewDetailResponse(
        Long applicationId,
        String applicantId,
        ApplicationStatus status,
        String coverLetterText,
        ReviewSelectedExperienceDto selectedExperience,
        List<FollowupItemDto> followup,
        List<String> interviewRecommendations
) {
}
