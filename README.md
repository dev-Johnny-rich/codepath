# CodePath

CodePath 是一个会诊断错误根因的 AI 编程教练。学生可以在网页中编写和运行代码，查看评测结果，获得分层提示、错因诊断和个性化练习建议。

## 项目结构

```text
codepath-web/                         React + TypeScript + Monaco Editor
codepath-backend/business-service/    Spring Boot 业务 API
codepath-backend/ai-service/          FastAPI + DeepSeek AI 能力
codepath-backend/sandbox-worker/      Docker 代码沙箱 Worker
codepath-backend/docker-compose.yml   本地完整环境
```

## 本地启动

### 1. 配置环境变量

```bash
cp codepath-backend/.env.example codepath-backend/.env
cp codepath-web/.env.example codepath-web/.env
```

请在本地 `.env` 中填写密码和 DeepSeek API Key。不要将 `.env` 提交到 Git。

### 2. 启动后端

```bash
cd codepath-backend
docker compose up -d --build
```

### 3. 启动前端

```bash
cd codepath-web
npm install
npm run dev
```

- 前端：`http://localhost:5173`
- 业务 API：`http://localhost:8081`
- AI 服务：`http://localhost:8000`

## 团队分工

- 前端：`codepath-web`
- Java 业务与数据库：`codepath-backend/business-service`
- AI、RAG 与沙箱：`codepath-backend/ai-service` 和 `codepath-backend/sandbox-worker`

所有变更从功能分支提交 Pull Request，经至少一名队友审核后合并。详见 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 安全要求

- 禁止提交 API Key、JWT Secret、数据库密码和真实 `.env`。
- 生产环境必须使用 HTTPS 并替换所有开发默认密钥。
- 生产沙箱必须运行在独立受限节点。
