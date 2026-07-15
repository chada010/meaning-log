-- 社区一致性：清理历史空壳数据、补齐级联约束、增加 Redis repair outbox 与缓存版本。

ALTER TABLE public_logs
    ADD COLUMN cache_version BIGINT NOT NULL DEFAULT 0 AFTER comment_count;

-- 先删除没有私人日志来源的空壳帖子，再清理其历史关联数据。
DELETE p FROM public_logs p
LEFT JOIN meaning_logs m ON m.id = p.log_id
WHERE m.id IS NULL;

DELETE n FROM notifications n
LEFT JOIN public_logs p ON p.id = n.public_log_id
WHERE n.public_log_id IS NOT NULL AND p.id IS NULL;

DELETE l FROM post_likes l
LEFT JOIN public_logs p ON p.id = l.public_log_id
WHERE p.id IS NULL;

DELETE c FROM post_comments c
LEFT JOIN public_logs p ON p.id = c.public_log_id
WHERE p.id IS NULL;

UPDATE post_comments child
LEFT JOIN post_comments parent ON parent.id = child.parent_id
SET child.parent_id = NULL
WHERE child.parent_id IS NOT NULL AND parent.id IS NULL;

UPDATE notifications n
LEFT JOIN post_comments c ON c.id = n.comment_id
SET n.comment_id = NULL
WHERE n.comment_id IS NOT NULL AND c.id IS NULL;

-- 从事实表校正现有冗余计数。
UPDATE public_logs p
LEFT JOIN (
    SELECT public_log_id, COUNT(*) AS cnt
    FROM post_likes
    GROUP BY public_log_id
) likes ON likes.public_log_id = p.id
LEFT JOIN (
    SELECT public_log_id, COUNT(*) AS cnt
    FROM post_comments
    GROUP BY public_log_id
) comments ON comments.public_log_id = p.id
SET p.like_count = COALESCE(likes.cnt, 0),
    p.comment_count = COALESCE(comments.cnt, 0),
    p.cache_version = p.cache_version + 1;

ALTER TABLE public_logs
    ADD CONSTRAINT fk_public_logs_log
        FOREIGN KEY (log_id) REFERENCES meaning_logs (id) ON DELETE CASCADE;

ALTER TABLE post_likes
    ADD CONSTRAINT fk_post_likes_public_log
        FOREIGN KEY (public_log_id) REFERENCES public_logs (id) ON DELETE CASCADE;

ALTER TABLE post_comments
    ADD CONSTRAINT fk_post_comments_public_log
        FOREIGN KEY (public_log_id) REFERENCES public_logs (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_post_comments_parent
        FOREIGN KEY (parent_id) REFERENCES post_comments (id) ON DELETE CASCADE;

ALTER TABLE notifications
    ADD CONSTRAINT fk_notifications_public_log
        FOREIGN KEY (public_log_id) REFERENCES public_logs (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_notifications_comment
        FOREIGN KEY (comment_id) REFERENCES post_comments (id) ON DELETE SET NULL;

CREATE TABLE community_redis_repairs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    repair_type VARCHAR(30) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    related_id BIGINT,
    attempt_count INT NOT NULL DEFAULT 0,
    last_error VARCHAR(500),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_community_redis_repairs_created (created_at, id)
);
