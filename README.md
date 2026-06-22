# Meaning Log

个人日记应用，内置 AI 伴侣「小记」。写下每日日志后，AI 自动生成标题、总结和标签，也可与小记多轮对话精修。支持游客试用，无需注册即可体验一次 AI 整理。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Spring Boot 3 · Java 17 · MyBatis-Plus · Spring Security (JWT) |
| 前端 | Vue 3 · TypeScript · Vite · Pinia · Element Plus |
| 数据库 | MySQL 8 · Redis（限流） |
| AI | 阿里云 DashScope / Qwen（OpenAI 兼容接口） |

## 功能截图

### 登录 / 注册

<table>
  <tr>
    <td><img src="docs/screenshots/login.png" width="480"/></td>
    <td><img src="docs/screenshots/register.png" width="480"/></td>
  </tr>
</table>

### 日志主页

![主页](docs/screenshots/home.png)

### 新增日志

![新增日志](docs/screenshots/new-log.png)

### 小记陪伴对话

![小记](docs/screenshots/xiaoji.png)

## 核心设计

- **Apply 模式**：AI 建议以草稿预览，用户确认后才落库，避免覆盖原始内容
- **多轮精修**：日志分析、AI 报告均支持与小记对话迭代优化
- **游客试用**：未登录用户可体验一次完整 AI 整理；注册后自动将试用结果转为第一篇日志
- **无状态鉴权**：JWT + token 版本号，改密码立即使所有旧 Token 失效

## 本地运行

**前置条件**：Java 17、MySQL 8、Redis、Node.js

```bash
# 后端
cd meaning-log-backend
DB_PASSWORD=<your-db-password> DASHSCOPE_API_KEY=<your-key> ./mvnw spring-boot:run

# 前端
cd meaning-log-frontend
npm install && npm run dev
# 访问 http://localhost:5173
```

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
