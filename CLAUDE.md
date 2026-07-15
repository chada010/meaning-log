# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目简介

Meaning Log 是一款个人日记应用，内置 AI 伴侣「小记」。用户写下每日日志后，AI 自动生成标题、总结和标签。支持游客试用模式，未登录用户可体验一次 AI 整理，无需注册。

## 常用命令

### 后端（`meaning-log-backend/`）

```bash
# 本地启动（需要 MySQL:3306 和 Redis:6379）
./mvnw spring-boot:run

# 打包 JAR
./mvnw clean package -DskipTests

# 运行所有测试
./mvnw test

# 运行单个测试类
./mvnw test -Dtest=类名
```

### 前端（`meaning-log-frontend/`）

```bash
npm run dev        # 开发服务器，地址 http://localhost:5173
npm run build      # 生产构建
npm run type-check # TypeScript 类型检查（不输出文件）
```

无 ESLint / Prettier / CheckStyle，没有代码风格检查命令。

## 架构说明

### 后端 — Spring Boot 3 / Java 17

包根路径：`com.chad.meaninglog`

调用链路：**Controller → Service → Repository（MyBatis-Plus）→ Entity**

| 层 | 说明 |
|---|---|
| `controller/` | REST 接口，统一前缀 `/api`。已登录用户通过 `@AuthenticationPrincipal UserAccount user` 注入。 |
| `service/` | 业务逻辑。`AiService` 负责编排 AI 调用；`AiRateLimiter` 通过 Redis 实现按 IP 限流。 |
| `repository/` | MyBatis-Plus `BaseMapper` 扩展。已开启下划线转驼峰映射。 |
| `client/OpenAiClient` | AI 业务编排入口（Prompt 组装、消息拼接）；底层由 `client/OpenAiTransport` 通过 Spring `RestClient` 调用 DeepSeek 的 OpenAI 兼容接口，并挂 Resilience4j `deepseek` 实例（Retry + CircuitBreaker + fallback）。 |
| `security/` | 无状态 JWT，通过 `JwtAuthenticationFilter` 实现。`UserAccount` 实现 `UserDetails`。 |
| `dto/` | 请求/响应对象，与 Entity 分离，响应 DTO 用静态 `from(entity)` 方法转换。 |

**无需 JWT 的公开接口**：`/api/auth/register`、`/api/auth/login`、`/api/auth/reset-password`、`/api/auth/send-code`、`/api/trial/**`

**AI 服务商**：DeepSeek，通过 `app.ai.*` 配置项注入。客户端向 `${app.ai.base-url}/chat/completions` 发送 OpenAI 格式请求（DeepSeek 兼容 OpenAI Chat Completions 协议）。所有提示词以静态常量形式定义在 `OpenAiClient` 中。

**Redis** 用于 AI 限流（`AiRateLimiter`）和邮箱验证码存储（`EmailVerificationService`），不做 Session 或缓存。

**图片** 以二进制 Blob 存储在 `log_image` 表中（`LogImage.data` 字段），不使用对象存储。

**数据库**：Flyway 版本化迁移（`resources/db/migration/` 下 V1 初始 schema、V2 ai_task、V3 community 三份），启动时自动执行。`spring.sql.init.mode=never`、`spring.flyway.enabled=true`、`baseline-on-migrate=true` 兼容已有数据库首次接入。

**JWT token 版本**：`UserAccount` 有 `tokenVersion` 字段，修改密码时递增，使所有旧 Token 立即失效。JWT 由 `JwtService` 手动实现（HMAC-SHA256），非 Spring Security 官方库。

### 前端 — Vue 3 + TypeScript + Vite

| 目录 | 职责 |
|---|---|
| `src/api/` | Axios 封装。`http.ts` 是唯一的 Axios 实例，统一处理 baseURL、JWT 注入和错误拦截。 |
| `src/stores/` | Pinia 状态管理。`authStore` 管理登录态；Token 存储在 localStorage，key 为 `meaning-log-token`。 |
| `src/views/` | 每个页面对应一个文件。 |
| `src/router/` | Vue Router，使用 `requiresAuth` / `guestOnly` 路由元守卫。鉴权只检查 localStorage 中的 Token，不发请求。 |

**localStorage 键**：

| Key | 用途 |
|---|---|
| `meaning-log-token` | JWT |
| `meaning-log-user` | 用户对象（与 token 同步存储） |
| `meaning-log-pending-trial` | 游客试用的原始日志草稿 |
| `meaning-log-trial-draft` | 游客试用的 AI 分析结果草稿 |

**错误处理**：`http.ts` 统一拦截所有请求错误，调用 `ElMessage.error()` 显示中文提示。AI 相关的 502/503 错误显示"AI 暂不可用"软提示，不影响日志的正常记录。

### 关键环境变量（后端）

| 变量 | 默认值 | 说明 |
|---|---|---|
| `DEEPSEEK_API_KEY` | 无（缺失时启动即报错） | AI 功能必填，DeepSeek API Key |
| `APP_AI_MODEL` | `deepseek-chat` | DeepSeek 模型名 |
| `APP_AI_BASE_URL` | `https://api.deepseek.com/v1` | 可替换为其他 OpenAI 兼容服务商 |
| `REDIS_HOST` / `REDIS_PORT` | `localhost:6379` | |
| `JWT_SECRET` | 开发占位符 | 生产环境必须替换 |
| `AI_RATE_LIMIT_MAX_REQUESTS` | `5` | 每个 IP 每窗口期最大请求数 |
| `AI_RATE_LIMIT_WINDOW_SECONDS` | `60` | 限流时间窗口（秒） |
| `MAIL_HOST` | `smtp.resend.com` | 当前使用 Resend SMTP；更换供应商时通过环境变量覆盖 |
| `MAIL_PORT` | `2465` | Resend 隐式 TLS 端口，可绕过部分平台对 465/587 的限制 |
| `MAIL_USERNAME` | `resend` | Resend 固定用户名 |
| `MAIL_PASSWORD` | — | Resend 仅发信 API Key，必须通过环境变量配置 |
| `MAIL_FROM` | — | 发件人地址，必须与 Resend 已验证域名匹配 |
| `MAIL_SMTP_SSL_ENABLE` | `true` | Resend 2465 使用隐式 TLS |
| `MAIL_SMTP_STARTTLS_ENABLE` | `false` | 使用隐式 TLS 时关闭 STARTTLS |
| `MAIL_CONNECTION_TIMEOUT_MS` | `10000` | SMTP 建连超时（毫秒） |
| `MAIL_READ_TIMEOUT_MS` | `10000` | SMTP 读取超时（毫秒） |
| `MAIL_WRITE_TIMEOUT_MS` | `10000` | SMTP 写入超时（毫秒） |
| `EMAIL_CODE_TTL` | `300` | 验证码有效期（秒） |
| `EMAIL_CODE_COOLDOWN` | `60` | 同一邮箱重新发送冷却时间（秒） |

### 登录方式

登录接口（`POST /api/auth/login`）接收 `identifier` 字段，支持邮箱或用户名两种方式：用正则 `^[^@\s]+@[^@\s]+\.[^@\s]+$` 判断是否为邮箱，分别走 `findByEmail` 或 `findByUsername` 查询。

### 邮箱验证码

注册前须先调用 `POST /api/auth/send-code` 获取 6 位验证码（TTL 5 分钟，同一邮箱 60 秒冷却）。验证码用 Redis 存储，key 为 `email:verify:code:<email>`；冷却 key 为 `email:verify:cooldown:<email>`。`AuthService.register()` 在写库前调用 `EmailVerificationService.verifyCode()` 校验，通过后立即删除 Redis key 防止重用。

本地启动统一从根目录 `.env` 读取 `MAIL_*` 配置，并由 `scripts/start-local.ps1` 通过子进程环境传给后端。脚本拒绝空值和 `.example` 发件域名，默认按工作区路径隔离 Docker Compose 项目；旧版数据卷可通过 `LOCAL_COMPOSE_PROJECT_NAME=meaning-log` 显式复用。如果 `8080` 或 `5173` 已被占用，则直接退出以避免复用未加载当前配置的进程。后端在所有启动方式下都会校验 `MAIL_PASSWORD` 和 `MAIL_FROM` 并对缺失或示例配置 fail-fast。使用 `scripts/stop-local.ps1` 停止脚本创建的应用进程及当前 Compose 依赖。不要将真实 Resend Key 写入 Git 跟踪文件、日志或启动命令行。

### CORS

后端 CORS 在 `SecurityConfig` 中硬编码允许 `localhost:5173` 及生产域名。部署时需修改 `SecurityConfig.corsConfigurationSource()`。

## AI 功能设计

四种 AI 交互模式，均在 `OpenAiClient` 中实现：

1. **日志分析**（`analyzeLog`）— 一次性生成：根据日志内容 + 可选图片，生成标题、总结、标签。
2. **日志对话精修**（`refineLogSummary`）— 对话式：用户与小记多轮对话，迭代优化 AI 输出；历史记录持久化在 `AiChatMessage`。
3. **陪伴聊天**（`chatWithCompanion`）— 开放式对话，入口为 `/xiaoji` 路由；会话存储在 `AiChatSession`。
4. **报告生成**（`summarizeReport` / `refineReport`）— 将多篇日志汇总为一段时期的报告，同样支持对话精修。

**Apply 模式**：AI 建议先以草稿形式预览（存储在 Entity 上），用户确认后通过独立的 `/apply` 接口显式落库。生成接口（`POST /logs/{id}/ai`）和精修接口均不自动持久化，需要前端显式调用 `/apply`。

## 游客试用流程

1. 游客在 `/trial` 页面提交日志，`POST /api/trial/analyze` 调用 AI（按 IP 限流，上限低于注册用户），结果**不入库**。
2. 前端将草稿和 AI 结果写入 localStorage（`meaning-log-pending-trial` / `meaning-log-trial-draft`）。
3. 用户注册或登录后，前端自动调用 `persistPendingTrial()` API，原子性地创建第一条日志并附带 AI 结果。
4. LocalStorage 草稿随即清除。

## 生产部署

### 架构

```
浏览器 HTTPS
  ↓ han.zhaisir.com
Gleam DNS (A → 76.76.21.21，代理关闭)
  ↓
Vercel
  ├─ /* → Vue 静态前端
  └─ /api/* → vercel.json 外部 Rewrite
                 ↓ 当前 trycloudflare.com Quick Tunnel
VPS 47.108.155.138 (阿里云成都 2GB)
  ├─ /etc/systemd/system/cf-tunnel.service (cloudflared → 127.0.0.1:8080)
  └─ /opt/meaning-log/docker-compose.prod.yml
       ├─ backend (Spring Boot :8080)
       ├─ mysql
       ├─ redis
       └─ rabbitmq
```

**为什么走 Tunnel**：阿里云 ECS 未做 ICP 关联时对海外来源 80/443 有网络层拦截，CF Flexible 报 521、certbot HTTP-01 也不通。Tunnel 是 VPS 主动出站，绕开入站拦截。

### VPS 访问

本地 `~/.ssh/config` 已配 `myvps` 别名（ed25519 免密）：

```bash
ssh myvps
```

Claude 应直接用 `ssh myvps '<cmd>'` 免密执行远程命令，不要让用户手动跑 VPS 命令。

### 单域名与 Quick Tunnel

生产入口为 `https://han.zhaisir.com`。前端生产构建固定使用 `VITE_API_BASE_URL=/api`；Vercel 根据 `meaning-log-frontend/vercel.json` 将 `/api/*` 转发到 Quick Tunnel，因此浏览器只访问同一个域名。

当前使用 `cloudflared tunnel --url http://localhost:8080` 的 **quick tunnel**，每次 `systemctl restart cf-tunnel` 都会换 URL。更新流程：

1. 查当前 URL：
   ```bash
   ssh myvps 'grep -oE "https://[a-z0-9-]+\.trycloudflare\.com" /var/log/cloudflared.log | tail -1'
   ```
2. 更新 `meaning-log-frontend/vercel.json` 中 Rewrite 的目标域名，保留 `/api/:path*` 路径映射。
3. 从仓库根目录强制部署 Vercel：`npx -y vercel@latest deploy --prod --force --yes --cwd .`。
4. 验证 `https://han.zhaisir.com/api/auth/login` 返回后端业务响应，而不是 Vercel 405 或 Cloudflare 错误页。

`han.zhaisir.com` 是免费二级域名，不控制一级域名 `zhaisir.com` 的 Cloudflare Zone，因此不能使用自有 Named Tunnel 绑定该 hostname。不要修改一级域名 Nameserver。

### 关键路径

VPS `/opt/meaning-log/`：
- `.env.prod` — 生产环境变量（11 字段全填）
- `docker-compose.prod.yml`
- `railway-dump.sql` — 从 Railway 迁出的历史数据（首次导入用，之后可归档）

VPS `/etc/systemd/system/cf-tunnel.service` — Cloudflare Tunnel systemd 单元

### Docker 镜像加速

VPS `/etc/docker/daemon.json`：
```json
{"registry-mirrors":["https://docker.1ms.run","https://docker.imgdb.de"]}
```
daocloud / tencentyun 镜像源已失效，勿使用。

### 常用命令

```bash
# 查当前 Quick Tunnel URL
ssh myvps 'grep -oE "https://[a-z0-9-]+\.trycloudflare\.com" /var/log/cloudflared.log | tail -1'

# 查容器状态
ssh myvps 'docker ps --format "table {{.Names}}\t{{.Status}}"'

# 查 backend 日志
ssh myvps 'docker compose -f /opt/meaning-log/docker-compose.prod.yml logs --tail 50 backend'

# 改 .env.prod 后重启 backend
ssh myvps 'cd /opt/meaning-log && docker compose -f docker-compose.prod.yml --env-file .env.prod up -d backend'

# 重启 Quick Tunnel（会换 URL！随后更新 vercel.json Rewrite 并重新部署）
ssh myvps 'systemctl restart cf-tunnel'
```

### 网络踩坑速查

- 本地 → GitHub：直连不通，`git push` 需带代理：`git -c http.proxy=http://127.0.0.1:7897 -c https.proxy=http://127.0.0.1:7897 push`
- VPS → GitHub：已配 `git config url."https://ghfast.top/https://github.com/".insteadOf`；`curl` release 用 `curl https://ghfast.top/https://github.com/...`
- VPS → `1.1.1.1` UDP 53 超时：`dig` 用 `@223.5.5.5`（阿里云 DNS）
