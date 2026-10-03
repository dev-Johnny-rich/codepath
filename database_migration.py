"""
数据库迁移工具 - Python 版本
用于执行 MySQL 迁移和 Neo4j 初始化
"""

import mysql.connector
from mysql.connector import Error
import os
from typing import Optional
from neo4j_init import Neo4jGraphInitializer
from config import config


class DatabaseMigration:
    """数据库迁移工具"""

    def __init__(self, mysql_config: dict, neo4j_config: dict):
        """
        初始化迁移工具

        Args:
            mysql_config: MySQL 配置 {"host": "", "user": "", "password": "", "database": ""}
            neo4j_config: Neo4j 配置 {"uri": "", "user": "", "password": ""}
        """
        self.mysql_config = mysql_config
        self.neo4j_config = neo4j_config

    def connect_mysql(self) -> Optional[mysql.connector.MySQLConnection]:
        """连接 MySQL 数据库"""
        try:
            connection = mysql.connector.connect(**self.mysql_config)
            if connection.is_connected():
                print(f"✓ 成功连接到 MySQL: {self.mysql_config['host']}")
                return connection
        except Error as e:
            print(f"✗ MySQL 连接失败: {e}")
            return None

    def execute_sql_file(self, connection: mysql.connector.MySQLConnection,
                        sql_file_path: str):
        """
        执行 SQL 文件

        Args:
            connection: MySQL 连接对象
            sql_file_path: SQL 文件路径
        """
        try:
            cursor = connection.cursor()

            # 读取 SQL 文件
            with open(sql_file_path, 'r', encoding='utf-8') as file:
                sql_script = file.read()

            # 分割 SQL 语句（处理存储过程和触发器）
            statements = self._split_sql_statements(sql_script)

            # 执行每条语句
            for i, statement in enumerate(statements, 1):
                statement = statement.strip()
                if not statement or statement.startswith('--'):
                    continue

                try:
                    cursor.execute(statement)
                    print(f"✓ 执行语句 {i}/{len(statements)}")
                except Error as e:
                    # 某些语句可能因为已存在而失败，这是正常的
                    if "already exists" in str(e) or "Duplicate" in str(e):
                        print(f"⚠ 语句 {i} 跳过（对象已存在）")
                    else:
                        print(f"✗ 语句 {i} 执行失败: {e}")
                        print(f"   语句: {statement[:100]}...")

            connection.commit()
            print(f"✓ SQL 文件执行完成: {sql_file_path}")

        except FileNotFoundError:
            print(f"✗ SQL 文件不存在: {sql_file_path}")
        except Exception as e:
            print(f"✗ 执行 SQL 文件失败: {e}")
            connection.rollback()
        finally:
            if cursor:
                cursor.close()

    def _split_sql_statements(self, sql_script: str) -> list:
        """
        智能分割 SQL 语句（处理存储过程和触发器中的分号）

        Args:
            sql_script: SQL 脚本内容

        Returns:
            list: SQL 语句列表
        """
        statements = []
        current_statement = []
        in_delimiter_block = False
        custom_delimiter = ';'

        lines = sql_script.split('\n')

        for line in lines:
            stripped_line = line.strip()

            # 检查 DELIMITER 命令
            if stripped_line.upper().startswith('DELIMITER'):
                delimiter_parts = stripped_line.split()
                if len(delimiter_parts) > 1:
                    custom_delimiter = delimiter_parts[1]
                in_delimiter_block = True
                continue

            # 如果在自定义分隔符块中
            if in_delimiter_block:
                current_statement.append(line)
                if stripped_line.endswith(custom_delimiter) and custom_delimiter != ';':
                    # 找到自定义分隔符，结束当前语句
                    statement = '\n'.join(current_statement)
                    statement = statement.replace(custom_delimiter, ';')
                    statements.append(statement.strip())
                    current_statement = []
                    in_delimiter_block = False
                    custom_delimiter = ';'
            else:
                # 普通语句，以分号分割
                current_statement.append(line)
                if stripped_line.endswith(';'):
                    statements.append('\n'.join(current_statement).strip())
                    current_statement = []

        # 添加最后一个语句（如果有）
        if current_statement:
            statements.append('\n'.join(current_statement).strip())

        return [s for s in statements if s and not s.startswith('--')]

    def verify_mysql_migration(self, connection: mysql.connector.MySQLConnection) -> bool:
        """
        验证 MySQL 迁移是否成功

        Args:
            connection: MySQL 连接对象

        Returns:
            bool: 是否成功
        """
        try:
            cursor = connection.cursor()

            # 检查关键表是否存在
            required_tables = [
                'knowledge_points',
                'exercise_knowledge_points',
                'student_submissions',
                'error_records',
                'mastery_snapshots',
                'recommendation_records'
            ]

            cursor.execute("SHOW TABLES")
            existing_tables = [table[0] for table in cursor.fetchall()]

            missing_tables = [t for t in required_tables if t not in existing_tables]

            if missing_tables:
                print(f"✗ 缺少表: {', '.join(missing_tables)}")
                return False

            print("✓ 所有必需的表都已创建")

            # 检查视图
            cursor.execute("SHOW FULL TABLES WHERE Table_type = 'VIEW'")
            views = [view[0] for view in cursor.fetchall()]
            print(f"✓ 已创建 {len(views)} 个视图")

            # 检查存储过程
            cursor.execute("SHOW PROCEDURE STATUS WHERE Db = %s",
                         (self.mysql_config['database'],))
            procedures = cursor.fetchall()
            print(f"✓ 已创建 {len(procedures)} 个存储过程")

            # 检查触发器
            cursor.execute("SHOW TRIGGERS")
            triggers = cursor.fetchall()
            print(f"✓ 已创建 {len(triggers)} 个触发器")

            return True

        except Error as e:
            print(f"✗ 验证失败: {e}")
            return False
        finally:
            if cursor:
                cursor.close()

    def migrate_mysql(self, sql_file_path: str = "mysql_migration.sql") -> bool:
        """
        执行 MySQL 迁移

        Args:
            sql_file_path: SQL 文件路径

        Returns:
            bool: 是否成功
        """
        print("=" * 60)
        print("开始 MySQL 数据库迁移")
        print("=" * 60)

        connection = self.connect_mysql()
        if not connection:
            return False

        try:
            # 执行迁移脚本
            self.execute_sql_file(connection, sql_file_path)

            # 验证迁移
            success = self.verify_mysql_migration(connection)

            if success:
                print("=" * 60)
                print("✅ MySQL 迁移成功完成！")
                print("=" * 60)
            else:
                print("=" * 60)
                print("⚠ MySQL 迁移部分成功，请检查错误信息")
                print("=" * 60)

            return success

        finally:
            if connection and connection.is_connected():
                connection.close()
                print("✓ MySQL 连接已关闭")

    def migrate_neo4j(self) -> bool:
        """
        执行 Neo4j 迁移

        Returns:
            bool: 是否成功
        """
        print("\n" + "=" * 60)
        print("开始 Neo4j 知识图谱初始化")
        print("=" * 60)

        try:
            from neo4j_init import initialize_python_course
            initialize_python_course()
            return True
        except Exception as e:
            print(f"✗ Neo4j 迁移失败: {e}")
            return False

    def full_migration(self, mysql_sql_path: str = "mysql_migration.sql") -> bool:
        """
        执行完整迁移（MySQL + Neo4j）

        Args:
            mysql_sql_path: MySQL 脚本路径

        Returns:
            bool: 是否全部成功
        """
        print("\n" + "█" * 60)
        print("█" + " " * 58 + "█")
        print("█" + " " * 15 + "Codepath 数据库完整迁移" + " " * 19 + "█")
        print("█" + " " * 58 + "█")
        print("█" * 60 + "\n")

        # 第一步：MySQL 迁移
        mysql_success = self.migrate_mysql(mysql_sql_path)

        # 第二步：Neo4j 迁移
        neo4j_success = self.migrate_neo4j()

        # 总结
        print("\n" + "=" * 60)
        print("迁移结果总结")
        print("=" * 60)
        print(f"MySQL 迁移: {'✅ 成功' if mysql_success else '❌ 失败'}")
        print(f"Neo4j 迁移: {'✅ 成功' if neo4j_success else '❌ 失败'}")
        print("=" * 60)

        if mysql_success and neo4j_success:
            print("\n🎉 恭喜！所有数据库迁移已成功完成！")
            print("\n下一步:")
            print("1. 启动 Redis 服务")
            print("2. 运行 BKT 算法测试: python bkt_algorithm.py")
            print("3. 测试推荐引擎: python recommendation_engine.py")
            print("4. 集成到后端 API")
            return True
        else:
            print("\n⚠️  部分迁移失败，请检查错误信息并重试")
            return False

    def rollback_mysql(self):
        """回滚 MySQL 迁移（删除新增的表）"""
        print("=" * 60)
        print("回滚 MySQL 迁移")
        print("=" * 60)

        connection = self.connect_mysql()
        if not connection:
            return

        try:
            cursor = connection.cursor()

            # 删除新增的表
            tables_to_drop = [
                'recommendation_records',
                'mastery_snapshots',
                'error_records',
                'student_submissions',
                'exercise_knowledge_points',
                'knowledge_prerequisites',
                'knowledge_points',
                'learning_sessions'
            ]

            for table in tables_to_drop:
                try:
                    cursor.execute(f"DROP TABLE IF EXISTS {table}")
                    print(f"✓ 删除表: {table}")
                except Error as e:
                    print(f"✗ 删除表 {table} 失败: {e}")

            # 删除视图
            cursor.execute("DROP VIEW IF EXISTS v_student_learning_overview")
            cursor.execute("DROP VIEW IF EXISTS v_knowledge_point_difficulty")
            print("✓ 删除视图")

            # 删除存储过程
            cursor.execute("DROP PROCEDURE IF EXISTS sp_create_daily_mastery_snapshot")
            print("✓ 删除存储过程")

            # 删除触发器
            cursor.execute("DROP TRIGGER IF EXISTS tr_after_submission_insert")
            cursor.execute("DROP TRIGGER IF EXISTS tr_after_submission_update_exercise")
            print("✓ 删除触发器")

            connection.commit()
            print("=" * 60)
            print("✅ MySQL 迁移回滚完成")
            print("=" * 60)

        except Exception as e:
            print(f"✗ 回滚失败: {e}")
            connection.rollback()
        finally:
            if connection and connection.is_connected():
                connection.close()


# ========================================
# 配置和使用示例
# ========================================

def main():
    """主函数"""
    # 从配置文件读取数据库连接信息
    mysql_config = config.mysql_config
    neo4j_config = config.neo4j_config

    print("=" * 60)
    print("读取配置信息:")
    print("=" * 60)
    print(config)
    print()

    # 创建迁移工具
    migration = DatabaseMigration(mysql_config, neo4j_config)

    # 执行完整迁移
    success = migration.full_migration("mysql_migration.sql")

    if not success:
        print("\n如需回滚，请运行: migration.rollback_mysql()")

    # 回滚示例（取消注释以执行）
    # migration.rollback_mysql()


if __name__ == "__main__":
    main()
