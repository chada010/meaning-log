# 压测:同步阻塞 vs MQ 异步(Track 2)

用 k6 + loadtest profile 隔离变量,对比"同步 AI 接口"和"202 异步入队接口"在 10 / 50 / 100 VU 三档并发下的 QPS、P95/P99、错误率,证明 Track 2 引入 RabbitMQ 的架构收益。

上游 AI 服务被 Mock 成固定 5s sleep,避免 DeepSeek 抖动 / 计费混入架构对比。

---

## 前置

### 1. k6(不用装,用 Docker 版)

已经装 Docker Desktop 就行 —— k6 image 已经拉到本地,后续所有跑法都通过 `docker run grafana/k6` 触发,包装在 `run.ps1` 里。

如果 k6 image 不在本地,手动拉一次:

```powershell
docker pull grafana/k6:latest
```

### 2. 起 Docker 依赖(MySQL / Redis / RabbitMQ)

在仓库根目录:

```powershell
docker compose up -d
```

等 15-30 秒让 MySQL healthcheck 通过。校验:

```powershell
docker ps
# 应看到 mysql / redis / rabbitmq 三个容器,STATUS 里都有 (healthy)
```

> 说明:压测流程不走 `start-local.ps1` —— 它会起默认后端占 8080,与 loadtest 后端冲突。这里只需要 Docker 依赖。

### 3. 起 loadtest profile 后端

**要求 8080 空闲**(如果之前跑过 `start-local.ps1`,先 `stop-local.ps1` 停掉)。

用现成脚本(会读 `.env`、切 profile、前台起后端):

```powershell
.\scripts\loadtest\start-backend.ps1
```

看到 `Started MeaningLogBackendApplication in ... seconds` 就 OK。这个终端保持开着,Ctrl+C 停止。

启动后 loadtest profile 会:

- 用 `LoadTestOpenAiTransport` 替换真实 DeepSeek 调用(5s sleep)
- 挂上 `POST /api/loadtest/logs/{id}/ai-sync` 端点(同步 baseline)
- 关闭 AI 限流与 AiTaskReaper

### 4. 准备测试账号 + 一条日志

已经有本地测试账号 `test` / `test1234` —— 直接登录建日志即可:

```powershell
# 登录拿 token
$LOGIN = curl.exe -s -X POST http://localhost:8080/api/auth/login `
  -H "Content-Type: application/json" `
  -d '{\"identifier\":\"test\",\"password\":\"test1234\"}' | ConvertFrom-Json
$TOKEN = $LOGIN.token
Write-Host "TOKEN=$TOKEN"

# 建一条日志,拿 LogId
$LOG = curl.exe -s -X POST http://localhost:8080/api/logs `
  -H "Authorization: Bearer $TOKEN" `
  -H "Content-Type: application/json" `
  -d '{\"content\":\"loadtest\",\"logDate\":\"2026-07-14\"}' | ConvertFrom-Json
$LOG_ID = $LOG.id
Write-Host "LOG_ID=$LOG_ID"
```

如果你原本已经有日志,登录后 `GET /api/logs` 拿任意 id 也行。

---

## sanity check(先手动打两个端点,免得空跑一小时)

沿用第 4 步已经拿到的 `$TOKEN` 和 `$LOG_ID` 变量(同一个 PowerShell 会话里):

```powershell
# 同步 baseline —— 应耗时 ~5s、返回 200
curl.exe -X POST -H "Authorization: Bearer $TOKEN" `
  "http://localhost:8080/api/loadtest/logs/$LOG_ID/ai-sync"

# 异步 —— 应立刻返回 202 + taskId
curl.exe -X POST -H "Authorization: Bearer $TOKEN" `
  "http://localhost:8080/api/logs/$LOG_ID/ai"
```

同步端点在**非 loadtest profile** 下会 404 —— 可以拿来验证 profile 隔离生效(切回默认后端再打一次)。

---

## 跑压测(六轮)

三档 VU × 两个端点。每轮记 QPS / P95 / P99 / 错误率。**LogId 替换成你自己的**。

### 同步 baseline

```powershell
.\scripts\loadtest\run.ps1 -Scenario sync -Vus 10  -Duration 15s -LogId 1
.\scripts\loadtest\run.ps1 -Scenario sync -Vus 50  -Duration 60s -LogId 1
.\scripts\loadtest\run.ps1 -Scenario sync -Vus 100 -Duration 60s -LogId 1
```

### 异步 202

```powershell
.\scripts\loadtest\run.ps1 -Scenario async -Vus 10  -Duration 15s -LogId 1
.\scripts\loadtest\run.ps1 -Scenario async -Vus 50  -Duration 60s -LogId 1
.\scripts\loadtest\run.ps1 -Scenario async -Vus 100 -Duration 60s -LogId 1
```

### 参数(可选覆盖)

`run.ps1` 支持以下参数:

| 参数 | 默认 | 说明 |
|---|---|---|
| `-BaseUrl` | `http://host.docker.internal:8080` | 后端地址(Docker 容器访问宿主) |
| `-UserEmail` | `test` | 测试账号(username 或 email 都行) |
| `-UserPassword` | `test1234` | |
| `-LogId` | (必填) | 已建的日志 id |
| `-Vus` | (必填) | 并发用户数 |
| `-Duration` | (必填) | 单档时长(如 `60s`) |

---

## 结果表(待实测填写)

k6 输出里读:

- **QPS** = `http_reqs` 的 `rate`(req/s)
- **P50 / P95 / P99** = `http_req_duration` 对应百分位(ms)
- **错误率** = `http_req_failed` 的 `rate`(0.0 ~ 1.0)

### 表 1:同步 baseline(`/api/loadtest/logs/{id}/ai-sync`)

| VU  | 时长 | QPS    | P50 (ms) | P95 (ms) | P99 (ms) | 错误率 |
|----:|----:|-------:|---------:|---------:|---------:|-------:|
| 10  | 15s | 1.96   | 5030     | 5050     | 5050     | 0.00%  |
| 50  | 60s | 9.82   | 5030     | 5110     | 5130     | 0.00%  |
| 100 | 60s | 19.64  | 5020     | 5100     | 5130     | 0.00%  |

### 表 2:异步 202(`/api/logs/{id}/ai`)

| VU  | 时长 | QPS    | P50 (ms) | P95 (ms) | P99 (ms) | 错误率 |
|----:|----:|-------:|---------:|---------:|---------:|-------:|
| 10  | 15s | 423.40 | 22       | 32       | 42       | 0.00%  |
| 50  | 60s | 283.05 | 108      | 160      | 194      | 0.03%  |
| 100 | 60s | 429.20 | 218      | 319      | 399      | 0.01%  |

> VU=50 那档 QPS 反低于 VU=100,原因是压测顺序:sync 三档 → async 三档,async 阶段开始时 MQ 已经堆积了前面 sync 阶段落库/前一档 async 入队的任务,消费者(concurrency=5,单条 mock 5s → 消费速率 ~1/s)持续争用 DB 连接,拖慢新入队的 202 响应。**这本身是压测发现的 back-pressure 现象**,是"如果 MQ 消费能力不足会怎样"的真实数据 —— 后续可通过调 concurrency、加消费者实例或分离消费/生产 DB 连接池优化。

### 汇总口径

> 在 VU=100 稳态下,同步接口 QPS **19.64**(受"每请求 5s + Tomcat 200 线程池"上限限制,理论天花板 40 req/s,实测已到 ~50% 上限),P99 **5130 ms**;
> 异步 202 QPS **429.20**,P99 **399 ms**;吞吐提升 **21.8 倍**,P99 延迟降低 **12.8 倍**。
>
> 面试卖点:同步接口的 QPS 上限严格等于 `线程池大小 / 单请求耗时`,一旦 AI 上游慢下来整条 HTTP 就废了;异步化把"等 AI"从 HTTP 生命周期挪到 MQ 消费者,HTTP 只做"入库 + 发消息",天花板从 20 拉到 400+,同时把长尾从 5s 压到 <400ms。

---

## 收尾

### 清 RabbitMQ 积压(异步压测后必做)

压测过程 HTTP 只入队不等消费,队列会积压几千条 mock 任务。下次跑前清一下:

```
浏览器打开 http://localhost:15672 → Queues → 找 ai.task.* 队列 → 点 Purge Messages
```

登录用 `guest` / `guest`。

### 停 loadtest 后端

`Ctrl+C` 关掉 loadtest 那个终端,再用主启动脚本正常起后端即可。
