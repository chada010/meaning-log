-- AI 异步任务表：Controller 202 入队后写入 PENDING，消费者原子门 PENDING→RUNNING，
-- 完成后 SUCCESS/FAILED；user_id 允许为空以兼容游客试用场景。
-- 索引 (user_id, created_at)：用户查询任务历史；(status, created_at)：消费者/reaper 扫状态。
CREATE TABLE IF NOT EXISTS ai_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT,
    task_type VARCHAR(32) NOT NULL,
    status VARCHAR(20) NOT NULL,
    input_json TEXT NOT NULL,
    result_json TEXT,
    error_message VARCHAR(512),
    retry_count INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_ai_task_user_created (user_id, created_at),
    INDEX idx_ai_task_status_created (status, created_at)
);
