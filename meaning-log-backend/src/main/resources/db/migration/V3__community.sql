-- 社区功能（Track 3）: public_logs / post_likes / post_comments / user_follows / notifications
CREATE TABLE IF NOT EXISTS public_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    log_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    published_at DATETIME NOT NULL,
    view_count BIGINT NOT NULL DEFAULT 0,
    like_count BIGINT NOT NULL DEFAULT 0,
    comment_count BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'VISIBLE',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_public_logs_log_id (log_id),
    INDEX idx_public_logs_published_at (published_at),
    INDEX idx_public_logs_user_id (user_id, published_at)
);

CREATE TABLE IF NOT EXISTS post_likes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    public_log_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_post_likes_post_user (public_log_id, user_id),
    INDEX idx_post_likes_user (user_id, created_at)
);

CREATE TABLE IF NOT EXISTS post_comments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    public_log_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    parent_id BIGINT,
    content VARCHAR(500) NOT NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_post_comments_post_created (public_log_id, created_at),
    INDEX idx_post_comments_user (user_id, created_at)
);

CREATE TABLE IF NOT EXISTS user_follows (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    follower_id BIGINT NOT NULL,
    followee_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_user_follows_pair (follower_id, followee_id),
    INDEX idx_user_follows_followee (followee_id, created_at)
);

CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    receiver_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    public_log_id BIGINT,
    comment_id BIGINT,
    content VARCHAR(255),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL,
    INDEX idx_notifications_receiver (receiver_id, is_read, created_at),
    INDEX idx_notifications_receiver_created (receiver_id, created_at)
);
