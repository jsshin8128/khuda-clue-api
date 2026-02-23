package com.khuda.khuda_clue_api.repository;

import com.khuda.khuda_clue_api.entity.FollowupQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * STAR 후속 질문 Repository
 */
public interface FollowupQuestionRepository extends JpaRepository<FollowupQuestion, Long> {

    /**
     * 특정 경험에 연결된 질문 목록을 조회합니다.
     *
     * @param experienceId 경험 ID
     * @return 질문 목록 (S/T/A/R 순서)
     */
    List<FollowupQuestion> findByExperienceIdOrderByTypeAsc(Long experienceId);
}
