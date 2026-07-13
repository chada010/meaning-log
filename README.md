# Meaning Log

一个 AI 辅助日记应用。

写完日志后，系统可以自动生成标题、总结和标签；你也可以继续和 AI 对话，迭代润色日志或报告。项目同时提供游客试用、日报总结、阶段性报告和“小记”陪伴聊天。

## 当前能力

- 用户注册、登录、重置密码、邮箱验证码
- 日志 CRUD、收藏、关键词/标签/日期筛选
- 日志图片上传与展示
- 单篇日志 AI 整理：标题、摘要、标签、对话精修、Apply 应用
- 日报总结与区间 AI 报告生成
- 报告二次对话与内容应用
- 游客试用一次 AI 整理，注册后自动承接结果
- 独立的“小记”陪伴聊天会话
- SSE 流式输出，适用于聊天和报告生成场景

## 技术栈

| 层 | 技术 |
| --- | --- |
| Frontend | Vue 3, TypeScript, Vite, Pinia, Vue Router, Element Plus |
| Backend | Spring Boot 3, Java 17, MyBatis-Plus, Spring Security, JWT |
| Database | MySQL 8 |
| Cache / Rate Limit | Redis |
| AI | DeepSeek OpenAI 兼容接口，默认模型 `deepseek-chat` |

## 项目结构

```text
meaning-log/
├─ meaning-log-frontend/   # Vue 3 前端
├─ meaning-log-backend/    # Spring Boot 后端
├─ docs/                   # 开发基线、验收清单、重构记录
└─ README.md
```

## 页面截图

### 登录 / 注册

<table>
  <tr>
    <td><img src="docs/screenshots/login.png" width="480" alt="login" /></td>
    <td><img src="docs/screenshots/register.png" width="480" alt="register" /></td>
  </tr>
</table>

### 日志主页

![home](docs/screenshots/home.png)

### 新建日志

![new-log](docs/screenshots/new-log.png)

### 小记聊天

![xiaoji](docs/screenshots/xiaoji.png)

## 本地运行

### 1. 前置条件

- Java 17
- Node.js 20+
- Docker Desktop（必需，用于 MySQL 与 Redis）

### 2. 首次配置

在仓库根目录创建本地运行变量文件：

```cmd
copy .env.example .env
```

从旧版升级且需要继续使用原有 `meaning-log_mysql-data` 和 `meaning-log_redis-data` 数据卷时，在 `.env` 设置：

```dotenv
LOCAL_COMPOSE_PROJECT_NAME=meaning-log
```

该兼容配置会让新脚本继续使用旧 Compose 项目及其数据卷。新工作区应保持此项为空，以便按工作区隔离数据。

复制后端与前端的本地配置样例：

```cmd
copy meaning-log-backend\application-local.properties.example meaning-log-backend\application-local.properties
copy meaning-log-frontend\.env.local.example meaning-log-frontend\.env.local
```

打开 `.env`，填写 Resend API Key，并将 `MAIL_FROM` 改为 Resend 已验证域名下的发件地址。Resend 使用 `smtp.resend.com:2465`、用户名 `resend` 和隐式 TLS。

打开 `meaning-log-backend/application-local.properties`，填写自己的 DeepSeek Key；示例已提供仅供本地开发的 JWT 密钥。生产环境必须设置 Base64 编码的随机 `JWT_SECRET`：

```properties
app.ai.api-key=your-deepseek-api-key
```

从旧版本升级时，如本地文件仍使用 `ai.api-key`，当前版本会继续兼容读取；下次编辑时将该键改为 `app.ai.api-key` 即可。

`.env`、`application-local.properties`、`application-docker.properties` 和 `.env.local` 均被 Git 忽略，真实密码与 API Key 不会进入提交。不要在 `.vscode/launch.json` 中重复保存 Resend Key。Docker Compose 会创建 `meaning_log` 数据库、`meaning_log` 本地数据库账号和 Redis；后端首次启动时自动执行 `schema.sql`。

首次运行前安装前端依赖：

```cmd
cd meaning-log-frontend
npm install
cd ..
```

### 3. 启动项目

确认 Docker Desktop 已启动，然后在仓库根目录运行统一启动脚本：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-local.ps1
```

脚本通过 Docker Compose 解析并校验 `.env`，拒绝空值和示例邮件配置，为当前工作区创建独立的 Docker Compose 项目，并启动和等待 MySQL、Redis、后端与前端就绪。健康等待覆盖 Compose 配置的首次初始化窗口；任一启动阶段失败时，脚本会停止本次新建的后端、前端进程与当前工作区的 Compose 依赖。启动后访问前端 [http://localhost:5173](http://localhost:5173)，后端监听 [http://localhost:8080](http://localhost:8080)。标准输出写入根目录 `logs/*-local.log`，错误输出写入 `logs/*-local-error.log`。

如果 `8080` 或 `5173` 已被占用，脚本会直接退出，避免把其他工作区或未加载当前 `.env` 的进程误判为启动成功。请先停止占用端口的进程，再重新运行脚本。

后端本身也会在 `MAIL_PASSWORD` 或 `MAIL_FROM` 缺失、仍为示例值或格式无效时拒绝启动，因此 IDE 和部署平台必须配置同样的邮件变量。

### 日常开发

首次配置完成后，日常仍使用统一启动脚本：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-local.ps1
```

停止当前工作区的后端、前端、MySQL 与 Redis 使用：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\stop-local.ps1
```

启动脚本会在 `logs/local-processes.json` 记录本次创建的应用进程，停止时核对 PID 与启动时间后再终止，避免误杀复用相同 PID 的其他进程。停止脚本不会删除数据卷。不要直接运行未指定项目名的 `docker compose down`，它可能命中其他工作区的同名项目；也不要随意使用 `-v`，该参数会删除本地 MySQL 与 Redis 数据卷。

## 关键配置

后端主要配置文件是 `meaning-log-backend/src/main/resources/application.properties`。

常用环境变量：

| 变量 | 说明 | 默认值 |
| --- | --- | --- |
| `MYSQLHOST` | MySQL 地址 | `localhost` |
| `MYSQLPORT` | MySQL 端口 | `3306` |
| `MYSQLDATABASE` | 数据库名 | `meaning_log` |
| `MYSQLUSER` | MySQL 用户 | `root`（兼容旧本机环境） |
| `DB_PASSWORD` | MySQL 密码 | `your-db-password-here` |
| `REDIS_HOST` | Redis 地址 | `localhost` |
| `REDIS_PORT` | Redis 端口 | `6379` |
| `REDIS_PASSWORD` | Redis 密码 | 空 |
| `LOCAL_COMPOSE_PROJECT_NAME` | Compose 项目名覆盖；仅在复用旧版数据卷时设为 `meaning-log` | 空，按工作区路径隔离 |
| `DEEPSEEK_API_KEY` | DeepSeek API Key（部署时使用） | 无，必须配置 |
| `APP_AI_BASE_URL` | AI 接口基地址 | `https://api.deepseek.com/v1` |
| `APP_AI_MODEL` | AI 模型名 | `deepseek-chat` |
| `JWT_SECRET` | Base64 编码的随机 JWT 密钥，解码后至少 32 字节 | 必须配置；本地可使用 `application-local.properties` 示例值 |
| `AUTH_TRUSTED_PROXY_CIDRS` | 可信反向代理 CIDR，多个值以逗号分隔 | 空，仅直连部署 |
| `AUTH_LOGIN_ATTEMPT_WINDOW_SECONDS` | 登录失败限流窗口（秒） | `900` |
| `AUTH_LOGIN_MAX_ATTEMPTS_PER_SOURCE` | 单来源登录尝试次数上限 | `20` / 15 分钟 |
| `AUTH_LOGIN_MAX_ATTEMPTS_PER_PRINCIPAL_SOURCE` | 单账号单来源登录尝试次数上限 | `5` / 15 分钟 |
| `MAIL_HOST` | SMTP Host | `smtp.resend.com` |
| `MAIL_PORT` | SMTP Port | `2465` |
| `MAIL_USERNAME` | SMTP 用户名 | `resend` |
| `MAIL_PASSWORD` | Resend 仅发信 API Key | 无，本地与部署环境必须配置 |
| `MAIL_FROM` | Resend 已验证域名下的发件地址 | 无，本地与部署环境必须配置 |
| `MAIL_SMTP_SSL_ENABLE` | 是否启用隐式 TLS | `true` |
| `MAIL_SMTP_STARTTLS_ENABLE` | 是否启用 STARTTLS | `false` |
| `MAIL_CONNECTION_TIMEOUT_MS` | SMTP 建连超时（毫秒） | `10000` |
| `MAIL_READ_TIMEOUT_MS` | SMTP 读取超时（毫秒） | `10000` |
| `MAIL_WRITE_TIMEOUT_MS` | SMTP 写入超时（毫秒） | `10000` |
| `EMAIL_CODE_SEND_MAX_ATTEMPTS_PER_SOURCE` | 单来源验证码发送次数上限 | `5` / 分钟 |
| `EMAIL_CODE_SEND_MAX_ATTEMPTS_GLOBAL` | 全局验证码发送次数上限 | `100` / 分钟 |
| `EMAIL_CODE_SEND_WINDOW_SECONDS` | 验证码发送限流窗口（秒） | `60` |

生产环境可使用以下 PowerShell 命令生成 JWT 密钥：

```powershell
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
[Convert]::ToBase64String($bytes)
```

### 反向代理

部署在 Nginx 或负载均衡器后时，必须将每个代理的专用地址显式配置到 `AUTH_TRUSTED_PROXY_CIDRS`，例如 `10.0.0.2/32,192.168.100.4/32`。该配置只接受 IPv4 `/32` 或 IPv6 `/128`，不能配置客户端网段或 `0.0.0.0/0`。同时，代理必须追加而非覆盖 `X-Forwarded-For`：

```nginx
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
```

未配置可信 CIDR 时，后端会忽略 `X-Forwarded-For` 并使用直连地址。

## 验证命令

```bash
# 前端类型检查
cd meaning-log-frontend
npm run type-check

# 前端构建
cd meaning-log-frontend
npm run build

# 后端测试
cd meaning-log-backend
./mvnw test
```

Windows 下最后一条改成：

```powershell
cd meaning-log-backend
.\mvnw.cmd test
```

## 开发说明

- `docs/development-baseline.md`：开发基线与分层约束
- `docs/manual-acceptance-checklist.md`：手工验收清单
- `docs/high-risk-areas.md`：高风险改动区域说明

## 持续集成

每个推送和 Pull Request 都会触发 GitHub Actions：前端执行类型检查与构建，后端在临时 MySQL/Redis 服务中执行测试。CI 不使用真实 DeepSeek Key，也不会调用外部模型接口。
