CREATE TABLE user_learning_assessment (
    user_id BIGINT PRIMARY KEY,
    overall_level VARCHAR(64) NOT NULL,
    score SMALLINT NOT NULL,
    summary TEXT NOT NULL,
    strengths_json TEXT NOT NULL,
    weaknesses_json TEXT NOT NULL,
    actions_json TEXT NOT NULL,
    next_focus VARCHAR(1000) NOT NULL,
    model_name VARCHAR(128),
    evidence_json TEXT NOT NULL,
    generated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_learning_assessment_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
