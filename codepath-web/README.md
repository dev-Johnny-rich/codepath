# CodePath Web

CodePath 第一版学生端，基于 React、TypeScript、Vite 和 Monaco Editor。

## 启动

先启动 `codepath-backend` 中的后端服务，再执行：

```bash
npm install
npm run dev
```

访问 <http://localhost:5173>。Vite 开发服务器会将 `/api` 代理到 `http://localhost:8081`，避免开发环境跨域问题。

## 已接入功能

- 课程和题目列表
- Monaco 代码编辑器
- 登录、注册和 JWT 持久化
- 提交评测与状态轮询
- 测试用例结果
- AI 错误诊断
- 三级分层提示
- 当前题目的提交历史
- Discuss 讨论广场：发帖、关联题目、搜索和回复
- Interview 测评档案：测评时间线、通过率和 AI 诊断摘要
- 游客免登录运行：临时凭证、沙箱测试和 AI 错误诊断

## 构建

```bash
npm run build
```

构建产物位于 `dist/`。“运行”允许游客使用题目测试用例快速检查代码；“提交评测”需要登录，并会写入提交历史、错题本和 Interview 档案。
