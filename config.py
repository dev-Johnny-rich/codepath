"""
配置管理模块 - 统一管理所有数据库连接和系统配置
支持从环境变量读取配置，避免硬编码敏感信息
"""

import os
from dotenv import load_dotenv

# 加载 .env 文件
load_dotenv()


class Config:
    """配置类 - 从环境变量读取配置"""

    # MySQL 配置
    MYSQL_HOST = os.getenv('MYSQL_HOST', 'localhost')
    MYSQL_PORT = int(os.getenv('MYSQL_PORT', 3306))
    MYSQL_USER = os.getenv('MYSQL_USER', 'root')
    MYSQL_PASSWORD = os.getenv('MYSQL_PASSWORD', '')
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
    NEO4J_URI = os.getenv('NEO4J_URI', 'bolt://localhost:7687')
    NEO4J_USER = os.getenv('NEO4J_USER', 'neo4j')
    NEO4J_PASSWORD = os.getenv('NEO4J_PASSWORD', '')

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
    REDIS_PORT = int(os.getenv('REDIS_PORT', 6379))
    REDIS_PASSWORD = os.getenv('REDIS_PASSWORD', None)
    REDIS_DB = int(os.getenv('REDIS_DB', 0))

    # 应用配置
    DEBUG = os.getenv('DEBUG', 'False').lower() == 'true'
    SECRET_KEY = os.getenv('SECRET_KEY', 'your-secret-key-change-in-production')

    # BKT 算法参数（可选）
    BKT_P_INIT = float(os.getenv('BKT_P_INIT', 0.0))
    BKT_P_LEARN = float(os.getenv('BKT_P_LEARN', 0.3))
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
  Debug: {self.DEBUG}
-----------------
"""

    def validate(self):
        """验证必需的配置是否已设置"""
        errors = []

        if not self.MYSQL_PASSWORD:
            errors.append("❌ MYSQL_PASSWORD 未设置")

        if not self.NEO4J_PASSWORD:
            errors.append("❌ NEO4J_PASSWORD 未设置")

        if self.SECRET_KEY == 'your-secret-key-change-in-production':
            errors.append("⚠️  SECRET_KEY 使用默认值，生产环境请修改")

        if errors:
            print("\n配置验证失败:")
            for error in errors:
                print(error)
            print("\n请检查 .env 文件或环境变量设置\n")
            return False

        print("✅ 配置验证通过")
        return True


# 全局配置实例
config = Config()


if __name__ == "__main__":
    # 测试配置
    print(config)
    config.validate()
