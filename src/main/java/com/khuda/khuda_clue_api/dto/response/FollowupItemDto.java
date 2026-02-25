package com.khuda.khuda_clue_api.dto.response;

/**
 * GET /applications/{id}/review 응답에 포함되는 STAR 질문·답변 한 쌍
 */
public record FollowupItemDto(
        String type,
        Long questionId,
        String questionText,
        String answerText
) {
}
