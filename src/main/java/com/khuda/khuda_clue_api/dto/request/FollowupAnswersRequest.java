package com.khuda.khuda_clue_api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

/**
 * STAR 후속 답변 제출 요청 DTO
 *
 * @param startedAt   지원자가 답변 작성을 시작한 시각 (ISO-8601, 클라이언트 제공)
 * @param submittedAt 지원자가 답변을 제출한 시각 (ISO-8601, 클라이언트 제공)
 * @param answers     질문별 답변 목록 (최소 1개)
 */
public record FollowupAnswersRequest(
        @NotNull LocalDateTime startedAt,
        @NotNull LocalDateTime submittedAt,
        @NotEmpty @Valid List<AnswerItem> answers
) {
}
