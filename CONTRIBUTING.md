# CodePath 协作规范

## 分支流程

1. 从最新 `main` 创建分支。
2. 在分支内完成一个边界清晰的任务。
3. 本地构建和测试通过后推送分支。
4. 创建 Pull Request，由至少一名队友审核。
5. 修复审核问题并等待 CI 通过后合并。

```bash
git switch main
git pull --ff-only
git switch -c feat/short-description
```

分支前缀：

- `feat/`：新功能
- `fix/`：缺陷修复
- `refactor/`：重构
- `docs/`：文档
- `chore/`：工程配置

## 提交信息

```text
feat: add teacher problem management
fix: prevent expired guest run access
refactor: isolate diagnosis prompt builder
docs: update local setup guide
chore: configure CI checks
```

## 共享文件

修改以下内容前，在 Issue 或团队群中同步：

- `codepath-backend/docker-compose.yml`
- `codepath-web/src/api.ts`
- API DTO 和接口路径
- Flyway 数据库迁移
- `.env.example`

## 数据库迁移

- 已合并的 Flyway 迁移文件不得修改。
- 当前版本为 V10，新迁移从 `V11__description.sql` 继续。
- 两名成员同时修改数据库时，必须先协调版本号。
- 迁移应同时提供最小验证方法。

## API 协作

后端接口变更的 Pull Request 必须写明：

- HTTP 方法和路径
- 是否需要 JWT
- 请求 JSON 示例
- 成功响应示例
- 错误状态码
- 前端需要同步的 TypeScript 类型

## 提交前检查

### 前端

```bash
cd codepath-web
npm run build
```

### Java 后端

```bash
cd codepath-backend/business-service
mvn test
```

### 完整环境

```bash
cd codepath-backend
docker compose up -d --build
docker compose ps
```

## Pull Request 要求

- 一个 PR 只处理一个主要目标。
- 不提交 `.env`、密钥、依赖目录或构建产物。
- 界面修改附截图，API 修改附 `curl` 或测试证据。
- 不在功能 PR 中顺便格式化无关文件。
- 作者不自行合并未审核的 PR。
