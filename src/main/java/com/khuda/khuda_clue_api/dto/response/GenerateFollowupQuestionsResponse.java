package com.khuda.khuda_clue_api.dto.response;

import com.khuda.khuda_clue_api.domain.ApplicationStatus;

import java.util.List;

/**
 * STAR 후속 질문 생성 응답 DTO
 *
 * @param applicationId 지원서 ID
 * @param status        지원서 상태 (QUESTIONS_SENT)
 * @param experienceId  선택된 경험 ID
 * @param questions     생성된 STAR 질문 목록 (4개)
 */
public record GenerateFollowupQuestionsResponse(
        Long applicationId,
        ApplicationStatus status,
        Long experienceId,
        List<QuestionDto> questions
) {
}
