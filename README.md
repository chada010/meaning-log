# Meaning Log

![Java 17](https://img.shields.io/badge/Java-17-ED8B00)
![Spring Boot 3.5](https://img.shields.io/badge/Spring%20Boot-3.5.14-6DB33F)
![Vue 3.5](https://img.shields.io/badge/Vue-3.5-4FC08D)
![TypeScript](https://img.shields.io/badge/TypeScript-3178C6)
![MySQL 8](https://img.shields.io/badge/MySQL-8-4479A1)
![Redis 7](https://img.shields.io/badge/Redis-7-DC382D)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3-FF6600)
![DeepSeek AI](https://img.shields.io/badge/AI-DeepSeek-6544A0)

🌐 **在线体验**:[www.chada010.freeddns.org](https://www.chada010.freeddns.org)

> **快速体验(免注册)**
> - 🎭 **游客试用**:访问 [/trial](https://www.chada010.freeddns.org/trial) 直接体验 AI 日志分析,无需登录
> - 🔒 **账号策略**:仓库不公开共享账号密码,避免访客互相污染日志、通知和 AI 额度

AI 辅助日记应用。用户写下日志后,AI「小记」自动生成标题、摘要、标签,并可与用户多轮对话精修输出。同时内置**社区模块**:日志一键发布到公开 feed,支持点赞、评论、关注、SSE 实时通知,形成"个人写作 → 公开分享"的完整闭环。

**技术侧作为个人简历项目**,围绕真实业务实现了 MQ 可靠投递、消费者原子抢占、MySQL 事实源 + Redis 可恢复投影、dirty counter 租约闭环、SSE 双通道容灾、Resilience4j 熔断等工程能力,并配有可重复的 Testcontainers/Flyway 验证和 k6 历史压测数据。

---

## 目录

- [架构一览](#架构一览)
- [核心工程能力](#核心工程能力)
- [性能压测](#性能压测)
- [技术栈](#技术栈)
- [API 端点](#api-端点)
- [本地运行](#本地运行)
- [环境变量](#环境变量)
- [持续集成](#持续集成)
- [开发文档](#开发文档)

---

## 架构一览

```mermaid
flowchart LR
    subgraph Client["客户端"]
        Web["Vue 3 前端<br/>Pinia · Element Plus"]
    end

    subgraph App["应用层 (Spring Boot 3.5)"]
        API["HTTP API<br/>REST + SSE"]
        Producer["AI Task Producer<br/>afterCommit 发消息"]
        NotifProd["Notification Producer"]
        Consumer["AI Consumer<br/>concurrency=5"]
        NotifCons["Notification Listener"]
        Reaper["AiTaskReaper<br/>PENDING 恢复投递<br/>RUNNING 超时清理"]
    end

    subgraph MQ["RabbitMQ"]
        AiQ[[ai.task.queue]]
        DLQ[[ai.task.dlq]]
        NotifQ[[notification.queue]]
    end

    subgraph Store["存储 & 缓存"]
        DB[("MySQL 8<br/>Flyway V1-V6")]
        Redis[("Redis 7<br/>可重建派生状态")]
    end

    subgraph Ext["外部服务"]
        AI([DeepSeek AI])
        SMTP([Resend SMTP])
    end

    Web -- "HTTP + JWT" --> API
    API -. "SSE 长连" .-> Web
    API --> Producer
    API --> NotifProd
    API --> DB
    API --> Redis
    Producer --> AiQ
    NotifProd --> NotifQ
    AiQ --> Consumer
    AiQ -.失败.-> DLQ
    NotifQ --> NotifCons
    Consumer --> DB
    Consumer -. Retry + 熔断 .-> AI
    NotifCons --> Redis
    Reaper --> DB
    API --> SMTP
```

- **前端**:Vue 3 + Pinia + Element Plus,Token 存 localStorage,SSE + 轮询双通道容灾。
- **后端**:Spring Boot 3.5,MVC + SSE,MyBatis-Plus 访问 MySQL,Spring Data Redis 直接操作 Redis 原语。
- **异步**:认证用户的 AI 分析、精修、报告和陪伴聊天走 RabbitMQ,HTTP 返回 202 + `taskId`;游客 `/trial` 保持直接流式体验。
- **数据**:Flyway V1-V6 启动自动迁移;测试覆盖空库 V1→V6 和已有 V4→V6 两条升级链。

---

## 核心工程能力

下面 6 项是项目的工程重点。每项都能在代码和自动化测试中找到对应证据,重点展示状态机、数据一致性和失败恢复,不把缓存或消息队列描述成天然可靠。

### 1. MQ 可靠投递:202 异步任务 + PENDING 恢复 + 原子抢占

原始设计:HTTP 直接调 DeepSeek,单次 5s。Tomcat 200 线程池被 5s 请求占满,压测 100 VU 只能撑到 **QPS ~20 / P99 5130 ms**。

改造后,认证用户的 AI 请求立即返回 202 + `taskId`,任务先写入 `ai_task(PENDING)`,事务提交后再尝试发 MQ。这避免了“数据库回滚但消息已经发出”的幽灵任务,但不假装 DB 与 MQ 已成为一个原子事务:如果 MQ 暂时不可用,任务仍保留在 MySQL,`AiTaskReaper` 会扫描滞留 `PENDING` 并重新投递。

消费者通过条件更新 `PENDING → RUNNING` 原子抢占执行权,重复消息只有一个消费者能执行;可重试异常恢复为 `PENDING`,重试耗尽进入 DLQ 后转 `FAILED`,超时 `RUNNING` 也由条件更新收口为 `FAILED`。成功状态统一为 `SUCCESS`。

**同一 mock 5s 压测下:QPS 429 / P99 399 ms,吞吐 21.8×、长尾 12.8×**(数字见 [性能压测](#性能压测))。

关键代码:

- [`service/AiTaskService.java`](meaning-log-backend/src/main/java/com/chad/meaninglog/service/AiTaskService.java) — 事务提交后触发投递
- [`service/AiTaskDeliveryService.java`](meaning-log-backend/src/main/java/com/chad/meaninglog/service/AiTaskDeliveryService.java) — 记录投递尝试与恢复发送
- [`mq/listener/AiTaskExecutor.java`](meaning-log-backend/src/main/java/com/chad/meaninglog/mq/listener/AiTaskExecutor.java) — 原子抢占与状态迁移
- [`mq/listener/AiTaskDlqListener.java`](meaning-log-backend/src/main/java/com/chad/meaninglog/mq/listener/AiTaskDlqListener.java) — 死信队列消费
- [`service/AiTaskReaper.java`](meaning-log-backend/src/main/java/com/chad/meaninglog/service/AiTaskReaper.java) — `PENDING` 恢复与 `RUNNING` 超时清理
- [`AiTaskDeliveryRecoveryIntegrationTest`](meaning-log-backend/src/test/java/com/chad/meaninglog/mq/AiTaskDeliveryRecoveryIntegrationTest.java) — MQ 失败、重复消息、retry、DLQ 与超时验证

### 2. MySQL 事实源 + Redis 可恢复投影

社区点赞、评论、关注、发布生命周期先在 MySQL 事务内落库并更新计数;Redis 只保存可重建的 Feed、热榜、Bitmap、关注集合和计数投影。事务内同时写入 `community_redis_repairs`,提交后立即尝试修复;Redis 不可用时修复记录留在 MySQL,定时任务继续重试。

为防旧修复覆盖新状态,帖子和关注投影都带 `cacheVersion`,Lua 只接受不小于当前版本的更新。定期 reconcile 还会从 MySQL 重新统计点赞/评论并批量重建 Bitmap 与热榜,验证“删 Redis key 后仍能恢复”。

- [`CommunityRedisRepairService`](meaning-log-backend/src/main/java/com/chad/meaninglog/service/community/CommunityRedisRepairService.java) — 事务内登记、提交后触发
- [`CommunityRedisRepairExecutor`](meaning-log-backend/src/main/java/com/chad/meaninglog/service/community/CommunityRedisRepairExecutor.java) — 独立事务执行与失败保留
- [`CommunityReconcileService`](meaning-log-backend/src/main/java/com/chad/meaninglog/service/community/CommunityReconcileService.java) — MySQL 对账与 Redis 重建

### 3. Bitmap 查询加速:MySQL Unique 保证幂等

点赞写路径不依赖 Redis 判重。`post_likes(public_log_id, user_id)` 的唯一约束和 `INSERT IGNORE` 语义决定是否首次点赞,随后在 MySQL 原子增减帖子计数;Redis Bitmap 只是 `isLiked` 高频查询的派生缓存。

修复任务从 MySQL 读取真实点赞关系,通过 Pipeline 构建临时 Bitmap 后再切换,避免重建过程中暴露半成品。即使 Bitmap 丢失,也能从关系表恢复,不会把缓存当成事实来源。

[`CommunityLikeService`](meaning-log-backend/src/main/java/com/chad/meaninglog/service/community/CommunityLikeService.java) · [`CommunityRedisBatchService`](meaning-log-backend/src/main/java/com/chad/meaninglog/service/community/CommunityRedisBatchService.java)

### 4. dirty counter 租约闭环:processing / ack / retry

浏览 PV 使用 HyperLogLog 去重,新增独立访客时才 `INCR` 浏览计数并把帖子 ID 放入 dirty Set。刷回任务不是“取出即删除”,而是用 Lua 把 ID 原子搬到带过期时间的 processing ZSet:

- MySQL 批量更新成功后 `ack`,从 processing 删除。
- 数据库失败时主动 `retry`,把 ID 放回 pending Set。
- 进程在处理中退出时,租约到期的 ID 会在下一轮自动回队。

这条闭环有数据库失败后重试成功的单测,避免通过禁用调度器掩盖状态问题。

[`CounterFlushJob`](meaning-log-backend/src/main/java/com/chad/meaninglog/service/community/job/CounterFlushJob.java) · [`CounterFlushJobTests`](meaning-log-backend/src/test/java/com/chad/meaninglog/service/community/CounterFlushJobTests.java)

### 5. SSE 双通道容灾:任务竞速 + 通知断线回退

AI 任务和通知都用带 JWT 的 Fetch SSE,避免原生 `EventSource` 无法自定义认证头的问题。

- **AI 任务**:`Promise.any([SSE, polling])` 竞速,轮询按 `3s → 6s → 12s → 24s → 30s` 退避,5 分钟超时;任一通道先观察到 `SUCCESS/FAILED` 即结束并清理另一个通道。
- **通知**:登录后先启动 30 秒未读数轮询并连接 SSE;收到 `ready` 后停轮询。断线时恢复轮询,同时按 `1s → 2s → ... → 30s` 重连;退出登录时通过 `AbortController` 关闭连接和定时器。
- **认证失效**:SSE 返回 401 时执行统一登出和登录页跳转,不保留后台死连接。

- 后端:[`web/SseEmitterSupport.java`](meaning-log-backend/src/main/java/com/chad/meaninglog/web/SseEmitterSupport.java) · [`AiTaskController`](meaning-log-backend/src/main/java/com/chad/meaninglog/controller/AiTaskController.java) · [`NotificationController`](meaning-log-backend/src/main/java/com/chad/meaninglog/controller/NotificationController.java)
- 前端:[`src/api/stream.ts`](meaning-log-frontend/src/api/stream.ts) · [`src/api/aiTask.ts`](meaning-log-frontend/src/api/aiTask.ts) · [`notificationStore.ts`](meaning-log-frontend/src/stores/notificationStore.ts)

### 6. Resilience4j 三件套:AI 调用不拖垮整个应用

`OpenAiTransport` 用 Resilience4j 挂了三层保护:

- **`@Retry`** — DeepSeek 偶发 5xx / 网络抖动时自动重试。
- **`@CircuitBreaker`** — 连续失败达阈值后短路,不再打 DeepSeek,直接走 fallback。
- **`fallback → AiUnavailableException`** — 认证用户任务写成 `FAILED + AI_UNAVAILABLE`,游客流式接口返回明确错误,前端显示“AI 暂不可用”;**日记 CRUD 主流程不受影响**。

保证 AI 上游挂掉时,日记核心功能完全无损。

[`client/OpenAiTransport.java`](meaning-log-backend/src/main/java/com/chad/meaninglog/client/OpenAiTransport.java) · [`client/AiUnavailableException.java`](meaning-log-backend/src/main/java/com/chad/meaninglog/client/AiUnavailableException.java)

---

## 性能压测

用 k6 隔离变量,对比 "HTTP 同步调 AI" vs "HTTP 202 + MQ 异步消费" 在相同并发下的表现。**上游 AI 被 Mock 成 5s sleep**(通过 `loadtest` profile 切换),只对比架构收益,不含 DeepSeek 抖动。

### AI 异步请求路径

```mermaid
sequenceDiagram
    autonumber
    participant U as Vue 前端
    participant API as HTTP API
    participant DB as MySQL
    participant MQ as RabbitMQ
    participant W as AI Consumer
    participant AI as DeepSeek

    U ->> API: POST /api/logs/{id}/ai
    API ->> DB: INSERT ai_task(PENDING)
    API -->> U: 202 { taskId }
    Note over API: TransactionSynchronization.afterCommit<br/>确保入库成功后再入队
    API ->> MQ: publish AiTaskMessage

    U ->> API: GET /api/ai/tasks/{id}/stream (SSE)
    Note over U,API: 前端同时轮询兜底<br/>3s → 6s → 12s → 24s → 30s

    MQ ->> W: deliver
    W ->> DB: UPDATE RUNNING
    W ->> AI: chat/completions
    Note over W,AI: @Retry + @CircuitBreaker<br/>失败进 ai.task.dlq
    AI -->> W: result
    W ->> DB: UPDATE SUCCESS + result
    W -->> API: notify
    API -->> U: SSE push done
```

### 结果 (VU=100 稳态, 60s)

> 以下是异步化初版的历史对比数据,用于说明“释放 HTTP 线程”的架构收益。本次新增 PENDING 恢复投递、原子抢占和 Redis 一致性修复后未重新跑 k6,因此不把这些数字当作当前版本性能承诺。

| 维度       | 同步 baseline | 异步 202     | 改善         |
|-----------|--------------:|-------------:|-------------:|
| QPS       | 19.64 /s     | 429.20 /s   | **21.8×**   |
| P50       | 5020 ms      | 218 ms      | 23×         |
| P95       | 5100 ms      | 319 ms      | 16×         |
| P99       | 5130 ms      | 399 ms      | **12.8×**   |
| 错误率     | 0.00%        | 0.01%       | 相当        |

**一句话**:同步接口的 QPS 上限严格等于 `Tomcat 线程池 / 单请求耗时`(200 / 5s = 40 理论上限,实测 ~20),AI 上游一慢整条 HTTP 就废。异步化把"等 AI"从 HTTP 生命周期挪到 MQ 消费者,天花板从 20 拉到 400+,长尾从 5s 压到 <400 ms。

完整的三档 VU 数据、跑法、以及压测暴露的 MQ back-pressure 现象讨论,见 [`scripts/loadtest/README.md`](scripts/loadtest/README.md)。

---

## 技术栈

| 层 | 技术 |
|---|---|
| 前端 | Vue 3.5 · TypeScript · Vite · Pinia · Vue Router · Element Plus |
| 后端 | Spring Boot 3.5.14 · Java 17 · MyBatis-Plus 3.5.16 · Spring Security · 手撸 JWT (HMAC-SHA256) |
| 数据库 | MySQL 8 · Flyway V1-V6 · 空库与 V4 升级链 Testcontainers 验证 |
| 缓存 & 数据结构 | Redis 7(ZSet · Bitmap · HyperLogLog · Set · String · Pipeline · Lua 版本栅栏) |
| 消息队列 | RabbitMQ 3(AI 任务队列 · DLQ · 通知队列) |
| 熔断 & 重试 | Resilience4j(Retry + CircuitBreaker + fallback) |
| API 文档 | Knife4j 4.5 + springdoc 2.7(OpenAPI 3) |
| AI | DeepSeek(OpenAI 兼容 Chat Completions,默认模型 `deepseek-chat`) |
| 邮件 | Resend SMTP(隐式 TLS 2465) |
| 压测 | k6(Docker 版) |
| CI | GitHub Actions(前端 type-check + build,后端 test on 临时 MySQL/Redis) |

---

## API 端点

启动后端后访问 [http://localhost:8080/doc.html](http://localhost:8080/doc.html) 查看 Knife4j UI,以下是核心端点摘要。

### 认证 `/api/auth`

| Method | Path | 说明 |
|---|---|---|
| POST | `/send-code` | 发送邮箱验证码(60s 冷却) |
| POST | `/register` | 注册(需邮箱验证码) |
| POST | `/login` | 登录(`identifier` 支持邮箱或用户名) |
| POST | `/reset-password` | 重置密码(触发 tokenVersion +1 强制下线所有旧 Token) |
| GET | `/me` | 当前用户信息 |

### 日志 CRUD `/api/logs`

| Method | Path | 说明 |
|---|---|---|
| GET | `/` | 列表(关键词 + 标签 + 日期 + 收藏筛选) |
| GET | `/{id}` | 详情 |
| GET | `/{id}/navigation` | 上一篇 / 下一篇 |
| POST | `/` | 新建 |
| PUT | `/{id}` | 更新 |
| PUT | `/{id}/favorite` | 收藏 toggle |
| DELETE | `/{id}` | 删除 |
| GET | `/images/{imageId}` | 图片(Blob 存 `log_image` 表) |

### AI 日志分析(异步) `/api/logs` + `/api/ai/tasks`

| Method | Path | 说明 |
|---|---|---|
| POST | `/api/logs/{id}/ai` | **入队 AI 分析,返回 202 + taskId** |
| POST | `/api/logs/{id}/ai/chat` | 对话精修(SSE) |
| GET | `/api/logs/{id}/ai/chat` | 精修历史消息 |
| POST | `/api/logs/{id}/ai/apply` | 显式应用 AI 结果到日志 |
| GET | `/api/ai/tasks/{taskId}` | 轮询任务状态 |
| GET | `/api/ai/tasks/{taskId}/stream` | **SSE 推送任务状态** |

### 报告 `/api/logs/ai/*`

| Method | Path | 说明 |
|---|---|---|
| POST | `/ai/daily-summary` | 当日总结 |
| POST | `/ai/report` | 区间 AI 报告 |
| GET | `/ai/reports` | 报告列表 |
| POST | `/ai/reports/{reportId}/chat` | 报告二次对话精修 |
| POST | `/ai/reports/{reportId}/apply` | 应用报告 |

### 小记陪伴 `/api/xiaoji`

| Method | Path | 说明 |
|---|---|---|
| GET | `/sessions` | 会话列表 |
| GET | `/sessions/{sessionId}/messages` | 消息历史 |
| POST | `/chat` | 陪伴聊天(SSE 流式) |

### 社区 `/api/community`

| Method | Path | 说明 |
|---|---|---|
| POST | `/publish/{logId}` | 发布日志到社区 |
| GET | `/feed` | Feed 流(Redis ZSet) |
| POST | `/like/{publicLogId}` | **Bitmap 幂等点赞** |
| POST | `/comments/{publicLogId}` | 评论(带敏感词过滤) |
| POST | `/follow/{userId}` | 关注 |
| GET | `/users/{userId}/posts` | 用户主页 |

### 通知 `/api/notifications`

| Method | Path | 说明 |
|---|---|---|
| GET | `/` | 通知列表(分页 + 已读过滤) |
| GET | `/unread-count` | 未读数 |
| POST | `/read-all` | 全部已读 |
| GET | `/stream` | **SSE 推送新通知** |

### 游客试用 `/api/trial`

| Method | Path | 说明 |
|---|---|---|
| POST | `/analyze` | AI 分析(IP 限流,**不入库**) |
| POST | `/analyze/stream` | 流式 AI 分析(SSE) |

<details>
<summary>其他辅助端点</summary>

- `GET /api/logs/ai/tags` — AI 建议标签
- `DELETE /api/community/publish/{logId}` — 撤回发布
- `GET /api/community/posts/{publicLogId}` — 帖子详情
- `GET /api/community/comments/{publicLogId}` — 评论列表
- `DELETE /api/community/like/{publicLogId}` — 取消点赞
- `DELETE /api/community/follow/{userId}` — 取关
- `GET /api/community/users/{userId}` — 用户 profile
- `POST /api/notifications/{id}/read` — 标记单条已读
- `GET /api/logs/ai/reports/{reportId}` — 报告详情
- `GET /api/logs/ai/reports/{reportId}/chat` — 报告对话历史

</details>

---

## 本地运行

### 1. 前置

- Java 17
- Node.js 20+
- Docker Desktop(必需,用于 MySQL / Redis / RabbitMQ)

### 2. 首次配置

复制配置样例:

```cmd
copy .env.example .env
copy meaning-log-backend\application-local.properties.example meaning-log-backend\application-local.properties
copy meaning-log-frontend\.env.local.example meaning-log-frontend\.env.local
```

打开 `.env`,填写 Resend API Key,并将 `MAIL_FROM` 改为 Resend 已验证域名下的发件地址。打开 `meaning-log-backend/application-local.properties`,填写自己的 DeepSeek Key。

`.env`、`application-local.properties`、`application-docker.properties`、`.env.local` 均已 Git 忽略,真实密钥不进提交。

安装前端依赖:

```cmd
cd meaning-log-frontend && npm install
```

### 3. 启动

确认 Docker Desktop 已启动,在仓库根目录:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-local.ps1
```

脚本会:解析并校验 `.env` → 起 Docker Compose(MySQL / Redis / RabbitMQ)→ 等待健康 → 起后端 → 起前端 → 记录 PID 到 `logs/local-processes.json`。

- 前端:[http://localhost:5173](http://localhost:5173)
- 后端:[http://localhost:8080](http://localhost:8080)
- Knife4j:[http://localhost:8080/doc.html](http://localhost:8080/doc.html)
- RabbitMQ 控制台:[http://localhost:15672](http://localhost:15672)(`guest` / `guest`)

如果 8080 / 5173 已占用,脚本会直接退出,避免误认已启动的其他工作区进程。

停止:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\stop-local.ps1
```

停止脚本核对 PID 与启动时间后再终止,不会删除数据卷。

### 4. 验证

```bash
cd meaning-log-frontend
npm run type-check
npm run build

cd ../meaning-log-backend
./mvnw test
```

Windows 下最后一行改成 `.\mvnw.cmd test`。

### 5. 压测(可选)

见 [`scripts/loadtest/README.md`](scripts/loadtest/README.md)。

---

## 环境变量

后端主要配置在 `meaning-log-backend/src/main/resources/application.properties`,可通过环境变量覆盖。

<details>
<summary>数据库 / Redis / Compose</summary>

| 变量 | 说明 | 默认 |
|---|---|---|
| `MYSQLHOST` | MySQL 地址 | `localhost` |
| `MYSQLPORT` | MySQL 端口 | `3306` |
| `MYSQLDATABASE` | 数据库名 | `meaning_log` |
| `MYSQLUSER` | MySQL 用户 | `root` |
| `DB_PASSWORD` | MySQL 密码 | — |
| `REDIS_HOST` | Redis 地址 | `localhost` |
| `REDIS_PORT` | Redis 端口 | `6379` |
| `REDIS_PASSWORD` | Redis 密码 | 空 |
| `LOCAL_COMPOSE_PROJECT_NAME` | Compose 项目名覆盖(仅在复用旧数据卷时设 `meaning-log`) | 空 |

</details>

<details>
<summary>AI / JWT / 限流</summary>

| 变量 | 说明 | 默认 |
|---|---|---|
| `DEEPSEEK_API_KEY` | DeepSeek API Key | 无,必须配置 |
| `APP_AI_BASE_URL` | AI 接口基地址 | `https://api.deepseek.com/v1` |
| `APP_AI_MODEL` | AI 模型名 | `deepseek-chat` |
| `AI_RATE_LIMIT_MAX_REQUESTS` | 每 IP 每窗口期最大请求数 | `5` |
| `AI_RATE_LIMIT_WINDOW_SECONDS` | 限流窗口(秒) | `60` |
| `JWT_SECRET` | Base64 编码 JWT 密钥(解码后 ≥32 字节) | 生产必须配 |
| `AUTH_TRUSTED_PROXY_CIDRS` | 可信反向代理 CIDR,逗号分隔 | 空 |
| `AUTH_LOGIN_ATTEMPT_WINDOW_SECONDS` | 登录失败限流窗口 | `900` |
| `AUTH_LOGIN_MAX_ATTEMPTS_PER_SOURCE` | 单来源尝试次数上限 | `20` |
| `AUTH_LOGIN_MAX_ATTEMPTS_PER_PRINCIPAL_SOURCE` | 单账号单来源上限 | `5` |

</details>

<details>
<summary>邮件 (Resend SMTP)</summary>

| 变量 | 说明 | 默认 |
|---|---|---|
| `MAIL_HOST` | SMTP Host | `smtp.resend.com` |
| `MAIL_PORT` | SMTP Port(隐式 TLS) | `2465` |
| `MAIL_USERNAME` | SMTP 用户名 | `resend` |
| `MAIL_PASSWORD` | Resend 仅发信 API Key | 必配 |
| `MAIL_FROM` | 已验证域名下的发件地址 | 必配 |
| `MAIL_SMTP_SSL_ENABLE` | 隐式 TLS | `true` |
| `MAIL_SMTP_STARTTLS_ENABLE` | STARTTLS | `false` |
| `MAIL_CONNECTION_TIMEOUT_MS` / `MAIL_READ_TIMEOUT_MS` / `MAIL_WRITE_TIMEOUT_MS` | SMTP 三段超时 | `10000` 各 |
| `EMAIL_CODE_TTL` | 验证码 TTL(秒) | `300` |
| `EMAIL_CODE_COOLDOWN` | 同邮箱重发冷却(秒) | `60` |
| `EMAIL_CODE_SEND_MAX_ATTEMPTS_PER_SOURCE` | 单来源发送上限 | `5` / 分钟 |
| `EMAIL_CODE_SEND_MAX_ATTEMPTS_GLOBAL` | 全局发送上限 | `100` / 分钟 |

</details>

<details>
<summary>生产 JWT 密钥生成 & 反向代理注意事项</summary>

PowerShell 生成 32 字节 Base64 JWT 密钥:

```powershell
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
[Convert]::ToBase64String($bytes)
```

部署在 Nginx / LB 后时,必须将每个代理的专用地址显式配置到 `AUTH_TRUSTED_PROXY_CIDRS`(如 `10.0.0.2/32,192.168.100.4/32`)。该配置只接受 IPv4 `/32` 或 IPv6 `/128`,不能配客户端网段或 `0.0.0.0/0`。同时代理必须追加而非覆盖 `X-Forwarded-For`:

```nginx
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
```

未配置可信 CIDR 时,后端会忽略 `X-Forwarded-For` 并使用直连地址。

</details>

---

## 持续集成

每个 push 和 PR 触发 GitHub Actions:

- 前端:`npm test` + `npm run type-check` + `npm run build`
- 后端:在临时 MySQL / Redis / RabbitMQ 服务中执行 `./mvnw test`

CI 不使用真实 DeepSeek Key,也不调用外部 AI 接口。

## 开发文档

- [`docs/development-baseline.md`](docs/development-baseline.md) — 开发基线与分层约束
- [`docs/manual-acceptance-checklist.md`](docs/manual-acceptance-checklist.md) — 手工验收清单
- [`docs/high-risk-areas.md`](docs/high-risk-areas.md) — 高风险改动区域
- [`scripts/loadtest/README.md`](scripts/loadtest/README.md) — 压测方案与完整数据
- [`CLAUDE.md`](CLAUDE.md) — 项目架构说明(Claude Code 用)
