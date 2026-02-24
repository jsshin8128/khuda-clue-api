package com.khuda.khuda_clue_api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * STAR 후속 질문 답변 단건 DTO
 *
 * @param questionId 질문 ID
 * @param answerText 답변 텍스트
 */
public record AnswerItem(
        @NotNull Long questionId,
        @NotBlank String answerText
) {
}
