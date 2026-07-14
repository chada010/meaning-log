CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    email VARCHAR(120) NOT NULL UNIQUE,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    token_version INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS meaning_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,
    log_date DATE NOT NULL,
    mood VARCHAR(30),
    ai_title VARCHAR(100),
    ai_summary TEXT,
    ai_tags VARCHAR(255),
    favorite BOOLEAN NOT NULL DEFAULT FALSE,
    user_id BIGINT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_meaning_logs_log_date (log_date),
    INDEX idx_meaning_logs_user_date (user_id, log_date, created_at)
);

CREATE TABLE IF NOT EXISTS log_images (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    meaning_log_id BIGINT NOT NULL,
    file_name VARCHAR(180),
    caption VARCHAR(160),
    content_type VARCHAR(80) NOT NULL,
    file_size BIGINT NOT NULL,
    display_order INT NOT NULL,
    data LONGBLOB NOT NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_log_images_log (meaning_log_id)
);

CREATE TABLE IF NOT EXISTS ai_reports (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    type VARCHAR(20) NOT NULL,
    title VARCHAR(120) NOT NULL,
    period VARCHAR(80) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    summary TEXT NOT NULL,
    tags VARCHAR(255),
    user_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_ai_reports_user_created (user_id, created_at)
);

CREATE TABLE IF NOT EXISTS ai_chat_sessions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    type VARCHAR(20) NOT NULL,
    title VARCHAR(120) NOT NULL,
    user_id BIGINT NOT NULL,
    meaning_log_id BIGINT,
    ai_report_id BIGINT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_ai_chat_sessions_user_type (user_id, type),
    INDEX idx_ai_chat_sessions_log (meaning_log_id),
    INDEX idx_ai_chat_sessions_report (ai_report_id)
);

CREATE TABLE IF NOT EXISTS ai_chat_messages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_ai_chat_messages_session_created (session_id, created_at)
);
