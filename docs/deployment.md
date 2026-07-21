# GitHub Actions 自动部署

## 流程

1. PR 和 `main` push 先执行 `.github/workflows/ci.yml`，后端通过 `mvn verify` 完成测试与 JAR 打包。
2. `main` 的 CI 全部成功后，`.github/workflows/deploy.yml` 构建后端 Docker 镜像。
3. 镜像使用完整 commit SHA 标记并推送到 GHCR。
4. GitHub Actions 通过 SSH 上传生产 Compose 文件，VPS 拉取指定镜像并重建后端容器。
5. 容器通过 `/actuator/health` 后部署成功；失败时脚本尝试恢复上一后端镜像。

`workflow_dispatch` 可手动部署已经进入 `main` 且通过 CI 的历史提交，用于受控重发或应用版本回滚。数据库迁移不会自动回滚，因此新增 Flyway 迁移必须保持向后兼容，或在部署前准备独立的数据恢复方案。

## GitHub Environment

创建名为 `vps-production` 的 Environment，并配置以下内容。

Secrets：

| 名称 | 说明 |
|---|---|
| `VPS_HOST` | VPS 域名或 IP |
| `VPS_USER` | 具备目标目录和 Docker 权限的部署用户 |
| `VPS_SSH_PRIVATE_KEY` | 专用部署私钥，不使用个人日常 SSH 私钥 |
| `VPS_KNOWN_HOSTS` | 已人工核对的 SSH host key 记录 |

Variables：

| 名称 | 说明 | 示例 |
|---|---|---|
| `VPS_PORT` | SSH 端口，未配置时使用 22 | `22` |
| `VPS_DEPLOY_PATH` | VPS 上现有生产部署目录，不能包含空格 | `/opt/meaning-log` |

`VPS_KNOWN_HOSTS` 可在可信网络中获取，保存前应与 VPS 控制台展示的主机指纹核对：

```bash
ssh-keyscan -p 22 your-vps.example.com
```

## VPS 一次性准备

- 安装 Docker Engine 和 Docker Compose Plugin。
- 保留现有生产目录及 `.env.prod`，真实密钥不进入 Git。
- 部署用户能够读取生产目录并执行 Docker 命令。
- Nginx 继续反代 `127.0.0.1:8080`，数据库、Redis 和 RabbitMQ 数据卷名称保持不变。

工作流会上传 `docker-compose.prod.yml.next`，校验通过后替换当前 Compose 文件；旧文件固定保留为 `docker-compose.prod.yml.rollback`。GHCR 登录使用本次部署专属的临时 Docker 配置目录，不覆盖部署用户的全局 Docker 凭据。部署过程不会删除数据库或 Docker volume。

## 首次启用

1. 合并部署 PR 前，先配置 `vps-production` Environment。
2. 首次执行建议给 Environment 增加人工审批，观察镜像拉取、Flyway 和健康检查日志。
3. 确认一至两次部署稳定后，可移除人工审批，实现 `main CI 成功 → 自动部署`。
4. 如果自动部署失败，先查看 Actions 中的后端日志和回滚结果，不要直接删除 volume 或清空数据库。

## 生产入口与 Tunnel（运行时）

GitHub Actions 只负责把后端镜像部署到 **App VPS**（阿里云，`/opt/meaning-log`）。公网 API 入口不在 Actions 里改写，而是：

1. 浏览器访问 `https://han.zhaisir.com/api/*`
2. Vercel `vercel.json` Rewrite 到 `https://api.chada010.me/api/:path*`
3. Cloudflare Named Tunnel hostname `api.chada010.me`
4. Tunnel **connector** 跑在可访问 Cloudflare Edge 的 **Relay VPS**（当前为 dedirock）
5. Relay 经 SSH RemoteForward 访问 App VPS 的 `127.0.0.1:8080`

运维细节与常用命令见根目录 `CLAUDE.md` 的「生产部署」章节。App VPS 本地 SSH 别名为 `zbvps`。

若 API 返回 Cloudflare 530/1033，优先检查 Relay 上 `cloudflared-meaning-log` 与 App 上 `meaning-log-relay`，而不是先怀疑账号密码或 GHCR 镜像。
