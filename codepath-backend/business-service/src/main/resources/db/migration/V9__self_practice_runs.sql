CREATE TABLE self_practice_input (
    submission_id BIGINT PRIMARY KEY,
    input_data MEDIUMTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_self_practice_submission FOREIGN KEY (submission_id)
        REFERENCES submission(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
