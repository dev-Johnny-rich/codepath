ALTER TABLE learning_plan
    ADD COLUMN regenerate_available_at DATETIME(6) NULL AFTER model_name;

UPDATE learning_plan
SET regenerate_available_at = DATE_ADD(created_at, INTERVAL 7 DAY)
WHERE regenerate_available_at IS NULL;

ALTER TABLE learning_plan_item
    ADD COLUMN completion_source VARCHAR(32) NULL AFTER status;

ALTER TABLE ai_generated_problem
    MODIFY COLUMN source_submission_id BIGINT NULL,
    ADD COLUMN plan_item_id BIGINT NULL AFTER source_submission_id,
    ADD KEY idx_ai_problem_plan_item (plan_item_id),
    ADD CONSTRAINT fk_ai_problem_plan_item FOREIGN KEY (plan_item_id)
        REFERENCES learning_plan_item(id) ON DELETE SET NULL;
