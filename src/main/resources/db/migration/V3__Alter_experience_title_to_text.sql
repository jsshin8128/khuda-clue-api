-- V3: Alter experience.title column from VARCHAR(255) to TEXT
-- Allows longer experience titles extracted from cover letters

ALTER TABLE experience 
MODIFY COLUMN title TEXT NOT NULL;
