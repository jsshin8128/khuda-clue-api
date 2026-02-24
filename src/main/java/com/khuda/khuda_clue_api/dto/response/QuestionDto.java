package com.khuda.khuda_clue_api.dto.response;

/**
 * STAR 후속 질문 DTO
 *
 * @param questionId   질문 ID
 * @param type         질문 유형 (S/T/A/R)
 * @param questionText 질문 내용
 */
public record QuestionDto(Long questionId, String type, String questionText) {
}
