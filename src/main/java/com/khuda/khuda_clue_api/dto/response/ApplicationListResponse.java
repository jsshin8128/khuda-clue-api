package com.khuda.khuda_clue_api.dto.response;

import java.util.List;

/**
 * 평가자 큐 목록 응답 DTO (커서 기반 페이지네이션)
 */
public record ApplicationListResponse(
        List<ApplicationListItemDto> items,
        String nextCursor
) {}
