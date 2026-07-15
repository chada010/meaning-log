-- ai_task 继续同时承担任务状态与耐久投递来源。
-- last_publish_at / publish_attempts 用于有界恢复扫描与投递观测；消费者仍以状态条件更新抢占执行权。
ALTER TABLE ai_task
    ADD COLUMN last_publish_at DATETIME NULL AFTER retry_count,
    ADD COLUMN publish_attempts INT NOT NULL DEFAULT 0 AFTER last_publish_at,
    ADD INDEX idx_ai_task_pending_publish (status, last_publish_at, created_at, id),
    ADD INDEX idx_ai_task_running_updated (status, updated_at, id);
