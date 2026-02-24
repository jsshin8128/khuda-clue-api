package com.khuda.khuda_clue_api.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * STAR 후속 질문에 대한 지원자 답변 엔티티
 * 질문 1개당 답변 1개 (uq_answer_one_per_question unique 제약으로 보장)
 */
@Entity
@Table(name = "followup_answer")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FollowupAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "answer_text", nullable = false, columnDefinition = "TEXT")
    private String answerText;

    /**
     * 지원자가 답변 작성을 시작한 시각 (클라이언트 제공)
     */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /**
     * 지원자가 답변을 제출한 시각 (클라이언트 제공)
     */
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public FollowupAnswer(Long questionId, String answerText, LocalDateTime startedAt, LocalDateTime submittedAt) {
        this.questionId = questionId;
        this.answerText = answerText;
        this.startedAt = startedAt;
        this.submittedAt = submittedAt;
    }
}
