# CodePath Backend

CodePath 的后端单仓库，第一阶段聚焦一条完整链路：

```text
创建代码提交 -> Redis 排队 -> 沙箱执行 -> 保存测试结果
-> AI 错因诊断 -> 更新知识点掌握度 -> 生成下一题推荐
```

## 服务边界

- `business-service`：Spring Boot 业务 API、MySQL 权威数据、提交状态机、掌握度与推荐。
- `ai-service`：FastAPI AI 服务、AST/规则分析、DeepSeek 诊断与失败降级。
- `sandbox-worker`：从 Redis 获取任务，在隔离 Docker 容器中执行不可信代码。

## 当前阶段

当前已完成可独立运行的后端主链路：

- 课程、知识点、编程题、测试用例数据库结构。
- 代码提交与状态机。
- Redis 执行队列和诊断队列。
- 沙箱任务拉取、Python 测试执行、结果回调协议。
- AI 服务的规则诊断、DeepSeek `deepseek-chat` 结构化诊断与规则降级。

DeepSeek 密钥仅通过 `.env` 的 `LLM_API_KEY` 注入，不写入代码或镜像。

## 本地启动

1. 复制 `.env.example` 为 `.env` 并修改本地密码。
2. 确保 Docker Desktop 已启动。
3. 执行 `docker compose up --build`。

业务 API 默认地址：`http://localhost:8081/api/v1`

AI 服务健康检查：`http://localhost:8000/health`

## 安全说明

沙箱 Worker 会挂载 Docker Socket，仅适合受控开发环境。生产环境应迁移到独立节点或专用沙箱系统，并继续强化 seccomp、AppArmor、镜像白名单和任务审计。
