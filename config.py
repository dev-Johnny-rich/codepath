"""
配置管理模块 - 统一管理所有数据库连接和系统配置
支持从环境变量读取配置，避免硬编码敏感信息
"""

import os
from pathlib import Path
from dotenv import load_dotenv

# 默认与真正运行的 Docker/Spring Boot 后端共用一份 .env。
# 可通过 CODEPATH_ENV_FILE 指向其他文件；真实系统环境变量始终优先。
PROJECT_ROOT = Path(__file__).resolve().parent
DEFAULT_ENV_FILE = PROJECT_ROOT / "codepath-backend" / ".env"
ENV_FILE = Path(os.getenv("CODEPATH_ENV_FILE", str(DEFAULT_ENV_FILE))).expanduser()
load_dotenv(ENV_FILE, override=False)


def _first_env(*names: str, default: str = "") -> str:
    for name in names:
        value = os.getenv(name)
        if value is not None:
            return value
    return default


class Config:
    """配置类 - 从环境变量读取配置"""

    # MySQL 配置
    MYSQL_HOST = os.getenv('MYSQL_HOST', 'localhost')
    MYSQL_PORT = int(os.getenv('MYSQL_PORT', 3307))
    MYSQL_USER = os.getenv('MYSQL_USER', 'codepath')
    MYSQL_PASSWORD = os.getenv('MYSQL_PASSWORD', 'codepath-dev')
    MYSQL_DATABASE = os.getenv('MYSQL_DATABASE', 'codepath')

    @property
    def mysql_config(self) -> dict:
        """返回 MySQL 连接配置字典"""
        return {
            "host": self.MYSQL_HOST,
            "port": self.MYSQL_PORT,
            "user": self.MYSQL_USER,
            "password": self.MYSQL_PASSWORD,
            "database": self.MYSQL_DATABASE
        }

    # Neo4j 配置
    NEO4J_URI = os.getenv('NEO4J_URI', 'bolt://localhost:7688')
    NEO4J_USER = _first_env('NEO4J_USERNAME', 'NEO4J_USER', default='neo4j')
    NEO4J_PASSWORD = os.getenv('NEO4J_PASSWORD', 'codepath-neo4j')

    @property
    def neo4j_config(self) -> dict:
        """返回 Neo4j 连接配置字典"""
        return {
            "uri": self.NEO4J_URI,
            "user": self.NEO4J_USER,
            "password": self.NEO4J_PASSWORD
        }

    # Redis 配置
    REDIS_HOST = os.getenv('REDIS_HOST', 'localhost')
    REDIS_PORT = int(os.getenv('REDIS_PORT', 6380))
    REDIS_PASSWORD = os.getenv('REDIS_PASSWORD', 'redis-dev')
    REDIS_DB = int(os.getenv('REDIS_DB', 0))

    # 应用配置
    DEBUG = os.getenv('DEBUG', 'False').lower() == 'true'
    APP_ENV = os.getenv('APP_ENV', 'development').lower()
    JWT_SECRET = _first_env(
        'JWT_SECRET',
        'SECRET_KEY',  # 兼容旧配置，新配置请使用 JWT_SECRET
        default='codepath-dev-jwt-secret-change-before-production-2026',
    )
    SECRET_KEY = JWT_SECRET

    # BKT 算法参数（可选）
    BKT_P_INIT = float(os.getenv('BKT_P_INIT', 0.15))
    BKT_P_LEARN = float(os.getenv('BKT_P_LEARN', 0.25))
    BKT_P_SLIP = float(os.getenv('BKT_P_SLIP', 0.1))
    BKT_P_GUESS = float(os.getenv('BKT_P_GUESS', 0.2))

    def __str__(self):
        """打印配置（隐藏密码）"""
        return f"""
配置信息:
-----------------
MySQL:
  Host: {self.MYSQL_HOST}:{self.MYSQL_PORT}
  User: {self.MYSQL_USER}
  Password: {'*' * len(self.MYSQL_PASSWORD) if self.MYSQL_PASSWORD else '(未设置)'}
  Database: {self.MYSQL_DATABASE}

Neo4j:
  URI: {self.NEO4J_URI}
  User: {self.NEO4J_USER}
  Password: {'*' * len(self.NEO4J_PASSWORD) if self.NEO4J_PASSWORD else '(未设置)'}

Redis:
  Host: {self.REDIS_HOST}:{self.REDIS_PORT}
  Password: {'*' * len(self.REDIS_PASSWORD) if self.REDIS_PASSWORD else '(未设置)'}
  DB: {self.REDIS_DB}

应用:
  Environment: {self.APP_ENV}
  Debug: {self.DEBUG}
  JWT Secret: {'*' * min(len(self.JWT_SECRET), 32) if self.JWT_SECRET else '(unconfigured)'}
-----------------
"""

    def validate(self):
        """验证必需的配置是否已设置"""
        errors = []

        if not self.MYSQL_PASSWORD:
            errors.append("❌ MYSQL_PASSWORD 未设置")

        if not self.NEO4J_PASSWORD:
            errors.append("❌ NEO4J_PASSWORD 未设置")

        if len(self.JWT_SECRET.encode("utf-8")) < 32:
            errors.append("❌ JWT_SECRET 必须至少 32 字节")

        development_secrets = {
            'your-secret-key-change-in-production',
            'codepath-dev-jwt-secret-change-before-production-2026',
        }
        if self.APP_ENV == 'production' and self.JWT_SECRET in development_secrets:
            errors.append("❌ 生产环境不能使用默认 JWT_SECRET")

        if self.APP_ENV == 'production':
            development_passwords = {
                'codepath-dev',
                'codepath-neo4j',
                'redis-dev',
                'change-me',
                'replace-neo4j-password',
                'change-redis-password',
            }
            for name, value in (
                ('MYSQL_PASSWORD', self.MYSQL_PASSWORD),
                ('NEO4J_PASSWORD', self.NEO4J_PASSWORD),
                ('REDIS_PASSWORD', self.REDIS_PASSWORD),
            ):
                if value in development_passwords:
                    errors.append(f"❌ 生产环境不能使用默认 {name}")

        if errors:
            print("\n配置验证失败:")
            for error in errors:
                print(error)
            print("\n请检查 .env 文件或环境变量设置\n")
            return False

        if self.APP_ENV != 'production' and self.JWT_SECRET in development_secrets:
            print("⚠️  当前使用开发环境 JWT_SECRET，上线前必须替换")

        print("✅ 配置验证通过")
        return True


# 全局配置实例
config = Config()


if __name__ == "__main__":
    # 测试配置
    print(config)
    config.validate()
