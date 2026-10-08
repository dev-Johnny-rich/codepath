CREATE TABLE ai_generated_problem (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    source_submission_id BIGINT,
    language VARCHAR(32) NOT NULL,
    difficulty TINYINT NOT NULL DEFAULT 1,
    title VARCHAR(160) NOT NULL,
    description MEDIUMTEXT NOT NULL,
    input_description TEXT,
    output_description TEXT,
    starter_code MEDIUMTEXT,
    stdin_data MEDIUMTEXT,
    evaluation_criteria MEDIUMTEXT NOT NULL,
    knowledge_point_ids VARCHAR(1000) NOT NULL,
    knowledge_summary TEXT NOT NULL,
    model_name VARCHAR(128),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_ai_problem_user_time (user_id, created_at),
    CONSTRAINT fk_ai_problem_submission FOREIGN KEY (source_submission_id)
        REFERENCES submission(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ai_practice_evaluation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    generated_problem_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    source_code MEDIUMTEXT NOT NULL,
    stdin_data MEDIUMTEXT,
    stdout_data MEDIUMTEXT,
    stderr_data MEDIUMTEXT,
    is_correct BOOLEAN NOT NULL,
    score DECIMAL(5,4) NOT NULL,
    feedback MEDIUMTEXT NOT NULL,
    error_type VARCHAR(64) NOT NULL,
    next_step TEXT,
    model_name VARCHAR(128),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_ai_evaluation_user_time (user_id, created_at),
    CONSTRAINT fk_ai_evaluation_problem FOREIGN KEY (generated_problem_id)
        REFERENCES ai_generated_problem(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
