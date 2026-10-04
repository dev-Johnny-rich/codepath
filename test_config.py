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
        print("1. 如果 Neo4j 密码不是 'neo4j123'，请修改 .env 文件")
        print("2. 运行：python database_migration.py")
        print("3. 提交代码到 Git（记得 .env 不会被提交）")
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
    print("2. .env 文件存在于项目根目录")
    sys.exit(1)
except Exception as e:
    print(f"❌ 配置测试失败: {e}")
    import traceback
    traceback.print_exc()
    sys.exit(1)
