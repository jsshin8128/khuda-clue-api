package com.khuda.khuda_clue_api.service;

import com.khuda.khuda_clue_api.entity.FollowupQuestion;

import java.util.List;

/**
 * STAR 후속 질문 생성 서비스 인터페이스
 * 선택된 경험을 기반으로 S/T/A/R 질문 4개를 생성합니다.
 */
public interface FollowupQuestionGenerationService {

    /**
     * 선택된 경험을 기반으로 STAR 질문 4개를 생성합니다.
     *
     * @param experienceId     경험 ID
     * @param experienceTitle  경험 제목 (자소서 발췌)
     * @param coverLetterText  자소서 원문 (경험 구간 포함)
     * @return S/T/A/R 질문 목록 (4개)
     */
    List<FollowupQuestion> generateFollowupQuestions(Long experienceId, String experienceTitle, String coverLetterText);
}
