package com.khuda.khuda_clue_api.entity;

import com.khuda.khuda_clue_api.domain.QuestionType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * STAR(Situation/Task/Action/Result) 후속 질문 엔티티
 * 선택된 경험 1개당 S/T/A/R 각 1개씩, 총 4개의 질문이 생성됨
 */
@Entity
@Table(name = "followup_question")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FollowupQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "experience_id", nullable = false)
    private Long experienceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private QuestionType type;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public FollowupQuestion(Long experienceId, QuestionType type, String questionText) {
        this.experienceId = experienceId;
        this.type = type;
        this.questionText = questionText;
    }
}
