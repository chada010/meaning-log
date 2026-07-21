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
  └─ /api/* → vercel.json 外部 Rewrite → https://api.chada010.me/api/:path*
                 ↓ Cloudflare Named Tunnel (hostname: api.chada010.me)
Relay VPS (dedirock / 海外，可连 Cloudflare Edge)
  ├─ cloudflared-meaning-log.service  (connector, protocol=http2)
  └─ 127.0.0.1:8080 ← SSH RemoteForward
                 ↓
App VPS 47.108.155.138 (阿里云成都 2GB)  SSH 别名: zbvps
  ├─ meaning-log-relay.service  (ssh -R 8080:127.0.0.1:8080 → dedirock)
  └─ /opt/meaning-log/docker-compose.prod.yml
       ├─ backend (Spring Boot 127.0.0.1:8080)
       ├─ mysql
       ├─ redis
       └─ rabbitmq
```

**为什么走 Tunnel**：阿里云 ECS 未做 ICP 关联时对海外来源 80/443 有网络层拦截；Tunnel 是主动出站。

**为什么 connector 不在阿里云本机**：该机出网到 Cloudflare Edge（QUIC/HTTP2 7844）不稳定或被拦，会报 530/1033。当前把 Named Tunnel connector 放在可访问 CF 的 relay 机，阿里云只跑业务容器，经 SSH 反代暴露 `127.0.0.1:8080`。

> 注意：relay 机上若另有服务占用 `127.0.0.1:8080`，需改端口或让出该端口（当前 sub2api 已改绑 `127.0.0.1:18081`）。

### VPS 访问

本地 `~/.ssh/config` 使用别名 **`zbvps`**（ed25519 免密，root@47.108.155.138:22）：

```bash
ssh zbvps
```

Agent 应直接用 `ssh zbvps '<cmd>'` 免密执行远程命令，不要让用户手动跑 VPS 命令。

### 单域名与 Named Tunnel

生产入口为 `https://han.zhaisir.com`。前端生产构建固定使用 `VITE_API_BASE_URL=/api`；Vercel 根据 `meaning-log-frontend/vercel.json` 将 `/api/*` 转发到 **`https://api.chada010.me`**（固定 Named Tunnel 域名，不再依赖 trycloudflare 随机 URL）。

`han.zhaisir.com` 是免费二级域名，不控制一级域名 `zhaisir.com` 的 Cloudflare Zone；API 使用自有域名 `api.chada010.me` 绑定 Named Tunnel。不要修改 `zhaisir.com` Nameserver。

### 关键路径与服务

App VPS `/opt/meaning-log/`：
- `.env.prod` — 生产环境变量（勿提交）
- `docker-compose.prod.yml`
- `railway-dump.sql` — 历史导入数据（可归档）

App VPS systemd：
- `meaning-log-relay.service` — SSH 反代到 relay（依赖 `/root/.ssh` 中 `dedirock-relay`）

Relay VPS：
- `/etc/meaning-log/cloudflared.env` — `TUNNEL_TOKEN` + `TUNNEL_TRANSPORT_PROTOCOL=http2`（勿提交）
- `cloudflared-meaning-log.service` — Named Tunnel connector

阿里云上旧的 `cf-tunnel.service`（Quick Tunnel）与本机 `cloudflared-meaning-log` 已停用，勿再当作生产入口。

### Docker 镜像加速

App VPS `/etc/docker/daemon.json`：
```json
{"registry-mirrors":["https://docker.1ms.run","https://docker.imgdb.de"]}
```
daocloud / tencentyun 镜像源已失效，勿使用。

### 常用命令

```bash
# 健康检查（公网）
curl -sS https://api.chada010.me/actuator/health
curl -sS -X POST https://han.zhaisir.com/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"identifier":"x","password":"y"}'

# App VPS：容器与反代
ssh zbvps 'docker ps --format "table {{.Names}}\t{{.Status}}"'
ssh zbvps 'systemctl status meaning-log-relay --no-pager'
ssh zbvps 'docker compose -f /opt/meaning-log/docker-compose.prod.yml logs --tail 50 backend'
ssh zbvps 'cd /opt/meaning-log && docker compose -f docker-compose.prod.yml --env-file .env.prod up -d backend'

# Relay：tunnel connector（在 dedirock 上）
ssh dedirock-01 'systemctl status cloudflared-meaning-log --no-pager'
ssh dedirock-01 'curl -sS http://127.0.0.1:8080/actuator/health'
```

### 故障速查

| 现象 | 优先检查 |
|---|---|
| 登录失败 / API 530 / Cloudflare 1033 | relay 上 `cloudflared-meaning-log` 是否 active；`curl 127.0.0.1:8080/actuator/health` |
| relay 本机 8080 不是 meaning-log | App 侧 `meaning-log-relay`；是否被其他容器占用 8080 |
| 后端本身 unhealthy | App 侧 `docker ps` / backend 日志 / `.env.prod` |

### 网络踩坑速查

- 本地 → GitHub：直连不通时，`git push` 带代理：`git -c http.proxy=http://127.0.0.1:7897 -c https.proxy=http://127.0.0.1:7897 push`
- App VPS → GitHub：可用 `ghfast.top` 镜像；`dig` 用 `@223.5.5.5`
- App VPS → Cloudflare Edge 7844：可能失败，这是 connector 迁到 relay 的原因
