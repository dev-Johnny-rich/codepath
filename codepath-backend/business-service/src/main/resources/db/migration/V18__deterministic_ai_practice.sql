ALTER TABLE test_case
    MODIFY problem_id BIGINT NULL,
    ADD COLUMN generated_problem_id BIGINT NULL AFTER problem_id,
    ADD KEY idx_test_case_generated_problem (generated_problem_id, sort_order),
    ADD CONSTRAINT fk_test_case_generated_problem FOREIGN KEY (generated_problem_id)
        REFERENCES ai_generated_problem(id) ON DELETE CASCADE;

ALTER TABLE submission
    MODIFY problem_id BIGINT NULL,
    ADD COLUMN ai_generated_problem_id BIGINT NULL AFTER problem_id,
    ADD KEY idx_submission_ai_problem (ai_generated_problem_id, user_id, created_at),
    ADD CONSTRAINT fk_submission_ai_problem FOREIGN KEY (ai_generated_problem_id)
        REFERENCES ai_generated_problem(id) ON DELETE SET NULL;

ALTER TABLE ai_generated_problem
    ADD COLUMN bkt_evidence_submission_id BIGINT NULL,
    ADD UNIQUE KEY uk_ai_problem_bkt_evidence (bkt_evidence_submission_id);

ALTER TABLE ai_practice_evaluation
    ADD COLUMN submission_id BIGINT NULL AFTER user_id,
    ADD COLUMN passed_count INT NOT NULL DEFAULT 0 AFTER stderr_data,
    ADD COLUMN total_count INT NOT NULL DEFAULT 0 AFTER passed_count,
    ADD COLUMN evidence_applied BOOLEAN NOT NULL DEFAULT FALSE AFTER total_count,
    ADD UNIQUE KEY uk_ai_evaluation_submission (submission_id),
    ADD CONSTRAINT fk_ai_evaluation_submission FOREIGN KEY (submission_id)
        REFERENCES submission(id) ON DELETE CASCADE;
