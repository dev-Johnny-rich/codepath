ALTER TABLE ai_generated_problem
    ADD COLUMN source_type VARCHAR(32) NOT NULL DEFAULT 'AI_PRACTICE' AFTER plan_item_id,
    ADD KEY idx_ai_problem_source_type (user_id, source_type, created_at);
