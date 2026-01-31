-- V2: Create experience table for CLUE system
-- Stores experience candidates extracted from cover letters

CREATE TABLE IF NOT EXISTS experience (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    application_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(255) NOT NULL,
    start_idx INT NOT NULL,
    end_idx INT NOT NULL,
    rank_score DOUBLE NOT NULL DEFAULT 0,
    is_selected TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    CONSTRAINT fk_experience_application
        FOREIGN KEY (application_id)
        REFERENCES application(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,

    KEY idx_experience_application_id (application_id),
    KEY idx_experience_application_selected (application_id, is_selected),
    KEY idx_experience_application_rank (application_id, rank_score DESC),
    KEY idx_experience_application_range (application_id, start_idx, end_idx)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- safety: allow only one selected experience per application
-- MySQL supports functional indexes; this keeps uniqueness for selected=1 only.
-- If you don't want this constraint in MVP, you can remove it.
CREATE UNIQUE INDEX uq_experience_one_selected_per_app
ON experience (application_id, (CASE WHEN is_selected = 1 THEN 1 ELSE NULL END));