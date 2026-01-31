package com.khuda.khuda_clue_api.service;

import com.khuda.khuda_clue_api.entity.Experience;

import java.util.List;

/**
 * 자소서에서 경험을 추출하는 서비스 인터페이스
 */
public interface ExperienceExtractionService {
    /**
     * 자소서 텍스트에서 경험을 추출합니다.
     *
     * @param applicationId 애플리케이션 ID
     * @param coverLetterText 자소서 텍스트
     * @return 추출된 경험 리스트 (최대 3개, rankScore 내림차순 정렬)
     */
    List<Experience> extractExperiences(Long applicationId, String coverLetterText);
}
