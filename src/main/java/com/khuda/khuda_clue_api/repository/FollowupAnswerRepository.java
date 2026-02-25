package com.khuda.khuda_clue_api.repository;

import com.khuda.khuda_clue_api.entity.FollowupAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * STAR 후속 답변 Repository
 */
public interface FollowupAnswerRepository extends JpaRepository<FollowupAnswer, Long> {

    /**
     * 질문 ID 목록에 해당하는 답변 목록을 조회합니다.
     *
     * @param questionIds 질문 ID 목록
     * @return 답변 목록
     */
    List<FollowupAnswer> findByQuestionIdIn(List<Long> questionIds);
}
