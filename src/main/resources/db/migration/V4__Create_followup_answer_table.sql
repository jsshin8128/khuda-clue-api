-- V4: followup_answer 테이블 생성
-- PR4: POST /applications/{id}/followup-answers (답변 제출 + 자동 추천 생성 / REVIEW_READY)

CREATE TABLE IF NOT EXISTS followup_answer (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    question_id BIGINT UNSIGNED NOT NULL,
    answer_text TEXT            NOT NULL,
    started_at  DATETIME        NULL,
    submitted_at DATETIME       NULL,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    CONSTRAINT fk_followup_answer_question
        FOREIGN KEY (question_id)
            REFERENCES followup_question (id)
            ON DELETE CASCADE
            ON UPDATE CASCADE,

    KEY idx_answer_question_id (question_id),
    KEY idx_answer_submitted_at (submitted_at)

) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 질문 1개당 답변 1개만 허용 (unique 제약)
CREATE UNIQUE INDEX uq_answer_one_per_question
    ON followup_answer (question_id);
