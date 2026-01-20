package com.khuda.khuda_clue_api.dto.response;

import com.khuda.khuda_clue_api.domain.ApplicationStatus;

import java.time.LocalDateTime;

public record SubmitResponse(
        Long applicationId,
        ApplicationStatus status,
        LocalDateTime createdAt
) {
}