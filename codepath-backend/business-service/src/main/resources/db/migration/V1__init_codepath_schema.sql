CREATE TABLE course (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    language VARCHAR(32) NOT NULL,
    description TEXT,
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE knowledge_point (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    difficulty TINYINT NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_knowledge_course_code (course_id, code),
    CONSTRAINT fk_knowledge_course FOREIGN KEY (course_id) REFERENCES course(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE knowledge_edge (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    source_id BIGINT NOT NULL,
    target_id BIGINT NOT NULL,
    edge_type VARCHAR(32) NOT NULL,
    weight DECIMAL(5,4) NOT NULL DEFAULT 1.0000,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_knowledge_edge (source_id, target_id, edge_type),
    CONSTRAINT fk_edge_course FOREIGN KEY (course_id) REFERENCES course(id),
    CONSTRAINT fk_edge_source FOREIGN KEY (source_id) REFERENCES knowledge_point(id),
    CONSTRAINT fk_edge_target FOREIGN KEY (target_id) REFERENCES knowledge_point(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE problem (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    slug VARCHAR(96) NOT NULL UNIQUE,
    title VARCHAR(160) NOT NULL,
    description MEDIUMTEXT NOT NULL,
    input_description TEXT,
    output_description TEXT,
    starter_code MEDIUMTEXT,
    function_signature VARCHAR(255),
    language VARCHAR(32) NOT NULL DEFAULT 'python',
    difficulty TINYINT NOT NULL DEFAULT 1,
    time_limit_ms INT NOT NULL DEFAULT 2000,
    memory_limit_mb INT NOT NULL DEFAULT 128,
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    KEY idx_problem_course_status (course_id, status),
    CONSTRAINT fk_problem_course FOREIGN KEY (course_id) REFERENCES course(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE problem_knowledge_point (
    problem_id BIGINT NOT NULL,
    knowledge_point_id BIGINT NOT NULL,
    weight DECIMAL(5,4) NOT NULL DEFAULT 1.0000,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (problem_id, knowledge_point_id),
    CONSTRAINT fk_pk_problem FOREIGN KEY (problem_id) REFERENCES problem(id) ON DELETE CASCADE,
    CONSTRAINT fk_pk_knowledge FOREIGN KEY (knowledge_point_id) REFERENCES knowledge_point(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE test_case (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    problem_id BIGINT NOT NULL,
    input_data MEDIUMTEXT,
    expected_output MEDIUMTEXT NOT NULL,
    is_hidden BOOLEAN NOT NULL DEFAULT TRUE,
    weight DECIMAL(6,2) NOT NULL DEFAULT 1.00,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_test_case_problem (problem_id, sort_order),
    CONSTRAINT fk_test_case_problem FOREIGN KEY (problem_id) REFERENCES problem(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE submission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    problem_id BIGINT NOT NULL,
    language VARCHAR(32) NOT NULL,
    source_code MEDIUMTEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    passed_count INT NOT NULL DEFAULT 0,
    total_count INT NOT NULL DEFAULT 0,
    runtime_ms BIGINT,
    memory_kb BIGINT,
    error_message MEDIUMTEXT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    started_at DATETIME(6),
    completed_at DATETIME(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    KEY idx_submission_user_time (user_id, created_at),
    KEY idx_submission_problem_time (problem_id, created_at),
    KEY idx_submission_status (status),
    CONSTRAINT fk_submission_problem FOREIGN KEY (problem_id) REFERENCES problem(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE submission_test_result (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    submission_id BIGINT NOT NULL,
    test_case_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    actual_output MEDIUMTEXT,
    stderr MEDIUMTEXT,
    runtime_ms BIGINT,
    memory_kb BIGINT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_submission_test (submission_id, test_case_id),
    CONSTRAINT fk_result_submission FOREIGN KEY (submission_id) REFERENCES submission(id) ON DELETE CASCADE,
    CONSTRAINT fk_result_test_case FOREIGN KEY (test_case_id) REFERENCES test_case(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE diagnosis (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    submission_id BIGINT NOT NULL UNIQUE,
    error_type VARCHAR(64) NOT NULL,
    root_cause TEXT NOT NULL,
    explanation MEDIUMTEXT NOT NULL,
    confidence DECIMAL(5,4),
    hint_level TINYINT NOT NULL DEFAULT 1,
    next_action VARCHAR(64),
    model_name VARCHAR(128),
    prompt_version VARCHAR(32),
    status VARCHAR(24) NOT NULL DEFAULT 'COMPLETED',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_diagnosis_submission FOREIGN KEY (submission_id) REFERENCES submission(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE diagnosis_evidence (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    diagnosis_id BIGINT NOT NULL,
    evidence_type VARCHAR(32) NOT NULL,
    line_number INT,
    content TEXT NOT NULL,
    source_reference VARCHAR(255),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_evidence_diagnosis (diagnosis_id),
    CONSTRAINT fk_evidence_diagnosis FOREIGN KEY (diagnosis_id) REFERENCES diagnosis(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE diagnosis_knowledge_point (
    diagnosis_id BIGINT NOT NULL,
    knowledge_point_id BIGINT NOT NULL,
    relevance DECIMAL(5,4) NOT NULL DEFAULT 1.0000,
    PRIMARY KEY (diagnosis_id, knowledge_point_id),
    CONSTRAINT fk_dk_diagnosis FOREIGN KEY (diagnosis_id) REFERENCES diagnosis(id) ON DELETE CASCADE,
    CONSTRAINT fk_dk_knowledge FOREIGN KEY (knowledge_point_id) REFERENCES knowledge_point(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE student_mastery (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    knowledge_point_id BIGINT NOT NULL,
    mastery DECIMAL(5,4) NOT NULL DEFAULT 0.0000,
    evidence_count INT NOT NULL DEFAULT 0,
    last_practiced_at DATETIME(6),
    next_review_at DATETIME(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_mastery_user_knowledge (user_id, knowledge_point_id),
    KEY idx_mastery_review (user_id, next_review_at),
    CONSTRAINT fk_mastery_knowledge FOREIGN KEY (knowledge_point_id) REFERENCES knowledge_point(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE mastery_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    knowledge_point_id BIGINT NOT NULL,
    submission_id BIGINT,
    event_type VARCHAR(48) NOT NULL,
    delta_value DECIMAL(6,5) NOT NULL,
    mastery_before DECIMAL(5,4) NOT NULL,
    mastery_after DECIMAL(5,4) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_mastery_event_user_time (user_id, created_at),
    CONSTRAINT fk_event_knowledge FOREIGN KEY (knowledge_point_id) REFERENCES knowledge_point(id),
    CONSTRAINT fk_event_submission FOREIGN KEY (submission_id) REFERENCES submission(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE recommendation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    problem_id BIGINT NOT NULL,
    knowledge_point_id BIGINT,
    score DECIMAL(8,5) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at DATETIME(6),
    KEY idx_recommendation_user_status (user_id, status, score),
    CONSTRAINT fk_recommendation_course FOREIGN KEY (course_id) REFERENCES course(id),
    CONSTRAINT fk_recommendation_problem FOREIGN KEY (problem_id) REFERENCES problem(id),
    CONSTRAINT fk_recommendation_knowledge FOREIGN KEY (knowledge_point_id) REFERENCES knowledge_point(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
