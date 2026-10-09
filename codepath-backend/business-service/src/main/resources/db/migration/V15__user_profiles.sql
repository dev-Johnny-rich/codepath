CREATE TABLE user_profile (
    user_id BIGINT PRIMARY KEY,
    gender VARCHAR(32),
    personality VARCHAR(500),
    country VARCHAR(120),
    school VARCHAR(200),
    family_address VARCHAR(300),
    major VARCHAR(200),
    habit VARCHAR(500),
    avatar_data_url MEDIUMTEXT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_user_profile_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
