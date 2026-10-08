CREATE TABLE learning_plan (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    goal VARCHAR(500) NOT NULL,
    current_level VARCHAR(32) NOT NULL,
    days_per_week TINYINT NOT NULL,
    minutes_per_day SMALLINT NOT NULL,
    duration_weeks TINYINT NOT NULL,
    title VARCHAR(160) NOT NULL,
    summary TEXT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    model_name VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    KEY idx_learning_plan_user_course (user_id, course_id, status, created_at),
    CONSTRAINT fk_learning_plan_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_learning_plan_course FOREIGN KEY (course_id) REFERENCES course(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE learning_plan_stage (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    plan_id BIGINT NOT NULL,
    week_number TINYINT NOT NULL,
    title VARCHAR(160) NOT NULL,
    objective VARCHAR(1000) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_plan_stage_order (plan_id, sort_order),
    CONSTRAINT fk_plan_stage_plan FOREIGN KEY (plan_id) REFERENCES learning_plan(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE learning_plan_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    stage_id BIGINT NOT NULL,
    knowledge_point_id BIGINT,
    problem_id BIGINT,
    task_type VARCHAR(24) NOT NULL,
    title VARCHAR(160) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    estimated_minutes SMALLINT NOT NULL,
    target_mastery DECIMAL(5,4) NOT NULL DEFAULT 0.8000,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    sort_order INT NOT NULL DEFAULT 0,
    completed_at DATETIME(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_plan_item_stage_order (stage_id, sort_order),
    CONSTRAINT fk_plan_item_stage FOREIGN KEY (stage_id) REFERENCES learning_plan_stage(id) ON DELETE CASCADE,
    CONSTRAINT fk_plan_item_knowledge FOREIGN KEY (knowledge_point_id) REFERENCES knowledge_point(id) ON DELETE SET NULL,
    CONSTRAINT fk_plan_item_problem FOREIGN KEY (problem_id) REFERENCES problem(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
