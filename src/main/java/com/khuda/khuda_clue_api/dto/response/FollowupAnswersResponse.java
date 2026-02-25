package com.khuda.khuda_clue_api.dto.response;

import com.khuda.khuda_clue_api.domain.ApplicationStatus;

/**
 * STAR 후속 답변 제출 + 면접 추천 질문 생성 응답 DTO
 *
 * @param applicationId 지원서 ID
 * @param status        최종 상태 (REVIEW_READY)
 * @param message       처리 완료 메시지
 */
public record FollowupAnswersResponse(
        Long applicationId,
        ApplicationStatus status,
        String message
) {
}
