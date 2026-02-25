package com.khuda.khuda_clue_api.dto.response;

/**
 * GET /applications/{id}/review 응답에 포함되는 선택된 경험 정보
 * (rankScore는 평가자 화면에 불필요하므로 제외)
 */
public record ReviewSelectedExperienceDto(
        Long experienceId,
        String title,
        Integer startIdx,
        Integer endIdx
) {
}
