CREATE TABLE discussion_post (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    problem_id BIGINT,
    title VARCHAR(180) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    tags VARCHAR(255),
    view_count INT NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL DEFAULT 'PUBLISHED',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    KEY idx_discussion_post_created (status, created_at),
    KEY idx_discussion_post_problem (problem_id, created_at),
    CONSTRAINT fk_discussion_post_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_discussion_post_problem FOREIGN KEY (problem_id) REFERENCES problem(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE discussion_comment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PUBLISHED',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_discussion_comment_post (post_id, created_at),
    CONSTRAINT fk_discussion_comment_post FOREIGN KEY (post_id) REFERENCES discussion_post(id) ON DELETE CASCADE,
    CONSTRAINT fk_discussion_comment_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
