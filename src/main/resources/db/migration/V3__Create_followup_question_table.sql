-- V3: Create followup_question table for CLUE system
-- STAR(Situation/Task/Action/Result) 질문을 저장하는 테이블
-- 선택된 경험(experience) 1개당 S/T/A/R 각 1개씩, 총 4개의 질문이 생성됨

CREATE TABLE IF NOT EXISTS followup_question (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    experience_id BIGINT UNSIGNED NOT NULL,
    type ENUM('S','T','A','R') NOT NULL,
    question_text TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    CONSTRAINT fk_followup_question_experience
        FOREIGN KEY (experience_id)
        REFERENCES experience(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,

    KEY idx_question_experience_id (experience_id),
    KEY idx_question_experience_type (experience_id, type)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- safety: experience당 STAR 타입별 질문 1개만 허용
CREATE UNIQUE INDEX uq_question_one_per_type_per_exp
ON followup_question (experience_id, type);
