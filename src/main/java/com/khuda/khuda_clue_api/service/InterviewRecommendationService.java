package com.khuda.khuda_clue_api.service;

import com.khuda.khuda_clue_api.entity.FollowupAnswer;
import com.khuda.khuda_clue_api.entity.FollowupQuestion;

import java.util.List;

/**
 * 면접 추천 질문 생성 서비스 인터페이스
 * 자소서 원문 + STAR Q&A를 기반으로 평가자가 면접에서 물어볼 추천 질문 3개를 생성한다.
 */
public interface InterviewRecommendationService {

    /**
     * 면접 추천 질문을 생성합니다.
     *
     * @param applicationId    지원서 ID (로깅 용도)
     * @param coverLetterText  자소서 원문
     * @param questions        STAR 후속 질문 목록 (4개)
     * @param answers          STAR 후속 답변 목록 (4개)
     * @return 추천 면접 질문 목록 (3개)
     */
    List<String> generateInterviewRecommendations(
            Long applicationId,
            String coverLetterText,
            List<FollowupQuestion> questions,
            List<FollowupAnswer> answers
    );
}
