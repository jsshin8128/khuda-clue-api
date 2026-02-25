package com.khuda.khuda_clue_api.dto.response;

import com.khuda.khuda_clue_api.domain.ApplicationStatus;

import java.time.LocalDateTime;

/**
 * 평가자 큐 목록의 개별 항목 DTO
 */
public record ApplicationListItemDto(
        Long applicationId,
        String applicantId,
        ApplicationStatus status,
        LocalDateTime createdAt
) {}
