-- 日志标题改为非必填：用户没写时留空，由 AI 分析后填入 ai_title
ALTER TABLE meaning_logs MODIFY COLUMN title VARCHAR(100) NULL;
