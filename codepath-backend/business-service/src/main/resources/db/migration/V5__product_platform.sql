CREATE TABLE app_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    email VARCHAR(190) NOT NULL UNIQUE,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    role VARCHAR(24) NOT NULL DEFAULT 'STUDENT',
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE app_user AUTO_INCREMENT = 1000;

CREATE TABLE refresh_token (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_refresh_user (user_id, expires_at),
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE course ADD COLUMN owner_user_id BIGINT NULL AFTER id;
ALTER TABLE problem ADD COLUMN created_by BIGINT NULL AFTER id;

CREATE TABLE wrong_book (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    problem_id BIGINT NOT NULL,
    latest_submission_id BIGINT,
    error_type VARCHAR(64),
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    notes TEXT,
    mistake_count INT NOT NULL DEFAULT 1,
    last_mistake_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    resolved_at DATETIME(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_wrong_user_problem (user_id, problem_id),
    KEY idx_wrong_user_status (user_id, status, last_mistake_at),
    CONSTRAINT fk_wrong_problem FOREIGN KEY (problem_id) REFERENCES problem(id),
    CONSTRAINT fk_wrong_submission FOREIGN KEY (latest_submission_id) REFERENCES submission(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ai_conversation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    submission_id BIGINT,
    title VARCHAR(160) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    KEY idx_conversation_user (user_id, updated_at),
    CONSTRAINT fk_conversation_submission FOREIGN KEY (submission_id) REFERENCES submission(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ai_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    model_name VARCHAR(128),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_message_conversation (conversation_id, id),
    CONSTRAINT fk_message_conversation FOREIGN KEY (conversation_id) REFERENCES ai_conversation(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE teaching_resource (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    knowledge_point_id BIGINT,
    title VARCHAR(200) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    source_url VARCHAR(1000),
    status VARCHAR(24) NOT NULL DEFAULT 'PUBLISHED',
    created_by BIGINT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    KEY idx_resource_course (course_id, status),
    FULLTEXT KEY ft_resource_text (title, content),
    CONSTRAINT fk_resource_course FOREIGN KEY (course_id) REFERENCES course(id),
    CONSTRAINT fk_resource_knowledge FOREIGN KEY (knowledge_point_id) REFERENCES knowledge_point(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE failed_job (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    queue_name VARCHAR(128) NOT NULL,
    payload JSON NOT NULL,
    error_message TEXT,
    attempts INT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    retried_at DATETIME(6),
    KEY idx_failed_job_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
