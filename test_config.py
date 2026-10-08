#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
测试配置文件是否正确加载
"""

import sys
import os

# 添加当前目录到 Python 路径
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

try:
    from config import config

    print("=" * 50)
    print("🧪 配置测试")
    print("=" * 50)
    print()

    # 显示配置信息
    print(config)
    print()

    # 验证配置
    if config.validate():
        print()
        print("=" * 50)
        print("✅ 配置测试通过！")
        print("=" * 50)
        print()
        print("📝 下一步：")
        print("1. 主配置文件：codepath-backend/.env")
        print("2. 从宿主机运行工具时使用 MySQL 3307、Redis 6380、Neo4j 7688")
        print("3. 上生产环境前设置 APP_ENV=production 并替换 JWT_SECRET")
    else:
        print()
        print("=" * 50)
        print("❌ 配置验证失败")
        print("=" * 50)
        sys.exit(1)

except ImportError as e:
    print(f"❌ 导入配置失败: {e}")
    print()
    print("请确保：")
    print("1. python-dotenv 已安装: pip install python-dotenv")
    print("2. codepath-backend/.env 存在，或已通过 CODEPATH_ENV_FILE 指定配置")
    sys.exit(1)
except Exception as e:
    print(f"❌ 配置测试失败: {e}")
    import traceback
    traceback.print_exc()
    sys.exit(1)
