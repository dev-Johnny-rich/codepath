# 安全配置指南

## 📋 目录

1. [快速开始](#快速开始)
2. [环境变量配置](#环境变量配置)
3. [安全注意事项](#安全注意事项)
4. [Git 提交前检查](#git-提交前检查)

---

## 🚀 快速开始

### 1. 复制环境变量模板

```bash
cp .env.example .env
```

### 2. 编辑 .env 文件

使用文本编辑器打开 `.env` 文件，填写实际的配置信息：

```bash
# Linux/Mac
nano .env

# Windows
notepad .env
```

### 3. 填写必需的配置

**必需配置**（必须填写）：
- `MYSQL_PASSWORD` - MySQL 数据库密码
- `NEO4J_PASSWORD` - Neo4j 数据库密码
- `SECRET_KEY` - 应用密钥（生产环境必须修改）

**可选配置**（可以使用默认值）：
- Redis 密码（如果 Redis 没有设置密码可以留空）
- BKT 算法参数（使用默认值即可）

### 4. 验证配置

```bash
python config.py
```

如果看到 `✅ 配置验证通过`，说明配置正确。

---

## 🔐 环境变量配置

### MySQL 配置示例

```bash
MYSQL_HOST=localhost
MYSQL_PORT=3306
MYSQL_USER=root
MYSQL_PASSWORD=MySecurePassword123!
MYSQL_DATABASE=codepath
```

### Neo4j 配置示例

```bash
NEO4J_URI=bolt://localhost:7687
NEO4J_USER=neo4j
NEO4J_PASSWORD=Neo4jSecurePass456!
```

### Redis 配置示例

```bash
# 如果 Redis 没有密码，REDIS_PASSWORD 留空
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
REDIS_DB=0

# 如果 Redis 设置了密码
REDIS_PASSWORD=RedisPassword789!
```

---

## ⚠️ 安全注意事项

### ❌ 绝对不要做的事

1. **不要提交 .env 文件到 Git**
   ```bash
   # .env 文件已经在 .gitignore 中，但请再次确认
   git status  # 确保 .env 不在待提交列表中
   ```

2. **不要在代码中硬编码密码**
   ```python
   # ❌ 错误示例
   password = "my_password_123"
   
   # ✅ 正确示例
   from config import config
   password = config.MYSQL_PASSWORD
   ```

3. **不要在公共场合分享配置信息**
   - 不要截图包含密码的 .env 文件
   - 不要在聊天工具中直接发送密码
   - 使用密码管理工具（如 1Password、LastPass）共享

### ✅ 应该做的事

1. **使用强密码**
   - 至少 12 个字符
   - 包含大小写字母、数字和特殊符号
   - 每个数据库使用不同的密码

2. **定期更换密码**
   - 生产环境每 3-6 个月更换一次
   - 有安全事件时立即更换

3. **限制数据库访问**
   ```sql
   -- MySQL 只允许本地访问
   CREATE USER 'codepath'@'localhost' IDENTIFIED BY 'password';
   GRANT ALL ON codepath.* TO 'codepath'@'localhost';
   ```

---

## 📝 Git 提交前检查清单

### 每次提交前运行：

```bash
# 1. 检查是否有敏感文件
git status

# 2. 确认 .env 不在列表中
# 如果看到 .env，立即运行：
git reset HEAD .env

# 3. 检查代码中是否有硬编码密码
grep -r "password.*=" *.py | grep -v "config.py" | grep -v ".env"

# 4. 查看即将提交的内容
git diff --cached
```

### 如果不小心提交了密码怎么办？

**立即执行以下步骤：**

1. **从 Git 历史中移除敏感文件**
   ```bash
   # 从所有历史记录中删除文件
   git filter-branch --force --index-filter \
     "git rm --cached --ignore-unmatch .env" \
     --prune-empty --tag-name-filter cat -- --all
   
   # 强制推送到远程
   git push origin --force --all
   ```

2. **立即更换所有密码**
   - MySQL 密码
   - Neo4j 密码
   - Redis 密码（如果有）
   - SECRET_KEY

3. **检查 GitHub/GitLab 是否有缓存**
   - GitHub 会缓存提交内容，需要联系支持清除

---

## 🔧 开发环境 vs 生产环境

### 开发环境 (.env.development)

```bash
DEBUG=True
MYSQL_HOST=localhost
MYSQL_DATABASE=codepath_dev
SECRET_KEY=dev-secret-key-not-secure
```

### 生产环境 (.env.production)

```bash
DEBUG=False
MYSQL_HOST=production-db.example.com
MYSQL_DATABASE=codepath_prod
SECRET_KEY=random-generated-256-bit-key-here
```

### 使用不同环境

```python
# 在代码中根据环境加载不同配置
import os
from dotenv import load_dotenv

env = os.getenv('ENVIRONMENT', 'development')
load_dotenv(f'.env.{env}')
```

---

## 🆘 常见问题

### Q1: .env 文件不生效？

**A:** 确保：
1. 文件名是 `.env`（注意开头的点）
2. 文件在项目根目录
3. 安装了 `python-dotenv` 包
4. 代码中调用了 `load_dotenv()`

### Q2: config.py 报错找不到环境变量？

**A:** 
```bash
# 检查 .env 文件是否存在
ls -la .env  # Linux/Mac
dir .env     # Windows

# 手动测试
python -c "from dotenv import load_dotenv; load_dotenv(); import os; print(os.getenv('MYSQL_PASSWORD'))"
```

### Q3: 如何在服务器上部署？

**A:** 
```bash
# 方式1: 使用 .env 文件（推荐用于小团队）
scp .env user@server:/path/to/project/

# 方式2: 使用环境变量（推荐用于生产环境）
export MYSQL_PASSWORD="your_password"
export NEO4J_PASSWORD="your_password"

# 方式3: 使用 Docker secrets（推荐用于容器化部署）
docker secret create mysql_password mysql_password.txt
```

---

## 📚 相关文档

- [INTEGRATION_GUIDE.md](INTEGRATION_GUIDE.md) - 系统集成指南
- [API_DOCUMENTATION.md](API_DOCUMENTATION.md) - API 文档
- [README.md](README.md) - 项目总览

---

## 🔗 有用的工具

- **密码生成器**: https://passwordsgenerator.net/
- **Git 敏感信息扫描**: https://github.com/trufflesecurity/trufflehog
- **环境变量管理**: https://www.vaultproject.io/

---

**⚡ 记住：安全配置是保护用户数据的第一道防线！**
