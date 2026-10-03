"""
Neo4j 知识图谱初始化脚本
用于自动创建课程知识图谱和学生掌握图谱
"""

from neo4j import GraphDatabase
import json
from typing import List, Dict, Any
from datetime import datetime
from config import config


class Neo4jGraphInitializer:
    """Neo4j 图谱初始化器"""

    def __init__(self, uri: str, user: str, password: str):
        self.driver = GraphDatabase.driver(uri, auth=(user, password))

    def close(self):
        self.driver.close()

    def clear_all_data(self):
        """清空所有数据（开发测试用）"""
        with self.driver.session() as session:
            session.run("MATCH (n) DETACH DELETE n")
            print("✓ 已清空所有节点和关系")

    def create_constraints_and_indexes(self):
        """创建约束和索引"""
        with self.driver.session() as session:
            constraints = [
                "CREATE CONSTRAINT course_id IF NOT EXISTS FOR (c:Course) REQUIRE c.course_id IS UNIQUE",
                "CREATE CONSTRAINT chapter_id IF NOT EXISTS FOR (ch:Chapter) REQUIRE ch.chapter_id IS UNIQUE",
                "CREATE CONSTRAINT knowledge_id IF NOT EXISTS FOR (k:KnowledgePoint) REQUIRE k.knowledge_id IS UNIQUE",
                "CREATE CONSTRAINT exercise_id IF NOT EXISTS FOR (e:Exercise) REQUIRE e.exercise_id IS UNIQUE",
                "CREATE CONSTRAINT student_id IF NOT EXISTS FOR (s:Student) REQUIRE s.student_id IS UNIQUE"
            ]

            indexes = [
                "CREATE INDEX knowledge_name IF NOT EXISTS FOR (k:KnowledgePoint) ON (k.name)",
                "CREATE INDEX exercise_difficulty IF NOT EXISTS FOR (e:Exercise) ON (e.difficulty)",
                "CREATE INDEX student_email IF NOT EXISTS FOR (s:Student) ON (s.email)"
            ]

            for constraint in constraints:
                session.run(constraint)
            print("✓ 已创建唯一约束")

            for index in indexes:
                session.run(index)
            print("✓ 已创建索引")

    def create_course(self, course_data: Dict[str, Any]):
        """创建课程节点"""
        with self.driver.session() as session:
            session.run("""
                CREATE (c:Course {
                    course_id: $course_id,
                    name: $name,
                    description: $description,
                    level: $level,
                    created_at: datetime()
                })
            """, **course_data)
            print(f"✓ 已创建课程: {course_data['name']}")

    def create_chapters(self, chapters: List[Dict[str, Any]]):
        """批量创建章节节点"""
        with self.driver.session() as session:
            for chapter in chapters:
                session.run("""
                    CREATE (ch:Chapter {
                        chapter_id: $chapter_id,
                        name: $name,
                        order: $order,
                        description: $description
                    })
                """, **chapter)
            print(f"✓ 已创建 {len(chapters)} 个章节")

    def create_knowledge_points(self, knowledge_points: List[Dict[str, Any]]):
        """批量创建知识点节点"""
        with self.driver.session() as session:
            for kp in knowledge_points:
                session.run("""
                    CREATE (k:KnowledgePoint {
                        knowledge_id: $knowledge_id,
                        name: $name,
                        description: $description,
                        difficulty: $difficulty,
                        prerequisites: $prerequisites
                    })
                """, **kp)
            print(f"✓ 已创建 {len(knowledge_points)} 个知识点")

    def create_exercises(self, exercises: List[Dict[str, Any]]):
        """批量创建练习题节点"""
        with self.driver.session() as session:
            for ex in exercises:
                session.run("""
                    CREATE (e:Exercise {
                        exercise_id: $exercise_id,
                        title: $title,
                        type: $type,
                        difficulty: $difficulty,
                        content: $content,
                        test_cases: $test_cases,
                        estimated_time: $estimated_time
                    })
                """, **ex)
            print(f"✓ 已创建 {len(exercises)} 个练习题")

    def link_course_chapters(self, course_id: str, chapter_ids: List[str]):
        """关联课程和章节"""
        with self.driver.session() as session:
            for i, chapter_id in enumerate(chapter_ids, 1):
                session.run("""
                    MATCH (course:Course {course_id: $course_id})
                    MATCH (ch:Chapter {chapter_id: $chapter_id})
                    CREATE (course)-[:CONTAINS {order: $order}]->(ch)
                """, course_id=course_id, chapter_id=chapter_id, order=i)
            print(f"✓ 已关联课程和 {len(chapter_ids)} 个章节")

    def link_chapter_knowledge(self, links: List[Dict[str, Any]]):
        """关联章节和知识点"""
        with self.driver.session() as session:
            for link in links:
                session.run("""
                    MATCH (ch:Chapter {chapter_id: $chapter_id})
                    MATCH (k:KnowledgePoint {knowledge_id: $knowledge_id})
                    CREATE (ch)-[:INCLUDES {order: $order}]->(k)
                """, **link)
            print(f"✓ 已关联章节和 {len(links)} 个知识点")

    def link_knowledge_prerequisites(self, links: List[Dict[str, str]]):
        """创建知识点依赖关系"""
        with self.driver.session() as session:
            for link in links:
                session.run("""
                    MATCH (k:KnowledgePoint {knowledge_id: $knowledge_id})
                    MATCH (prereq:KnowledgePoint {knowledge_id: $prerequisite_id})
                    CREATE (k)-[:REQUIRES]->(prereq)
                """, **link)
            print(f"✓ 已创建 {len(links)} 个知识点依赖关系")

    def link_exercise_knowledge(self, links: List[Dict[str, Any]]):
        """关联练习题和知识点"""
        with self.driver.session() as session:
            for link in links:
                session.run("""
                    MATCH (e:Exercise {exercise_id: $exercise_id})
                    MATCH (k:KnowledgePoint {knowledge_id: $knowledge_id})
                    CREATE (e)-[:COVERS {weight: $weight}]->(k)
                """, **link)
            print(f"✓ 已关联练习题和 {len(links)} 个知识点")

    def create_student(self, student_data: Dict[str, Any]):
        """创建学生节点"""
        with self.driver.session() as session:
            session.run("""
                CREATE (s:Student {
                    student_id: $student_id,
                    name: $name,
                    email: $email,
                    created_at: datetime(),
                    total_exercises: 0,
                    total_errors: 0
                })
            """, **student_data)
            print(f"✓ 已创建学生: {student_data['name']}")

    def update_student_mastery(self, student_id: str, knowledge_id: str,
                               level: float, attempts: int, correct: int, errors: int):
        """更新或创建学生知识点掌握度"""
        with self.driver.session() as session:
            session.run("""
                MATCH (s:Student {student_id: $student_id})
                MATCH (k:KnowledgePoint {knowledge_id: $knowledge_id})
                MERGE (s)-[m:MASTERY]->(k)
                SET m.level = $level,
                    m.attempts = $attempts,
                    m.correct_count = $correct,
                    m.error_count = $errors,
                    m.last_updated = datetime(),
                    m.learning_velocity = ($level / $attempts)
            """, student_id=student_id, knowledge_id=knowledge_id,
                       level=level, attempts=attempts, correct=correct, errors=errors)

    def record_student_error(self, student_id: str, knowledge_id: str,
                            error_data: Dict[str, Any]):
        """记录学生错误"""
        with self.driver.session() as session:
            session.run("""
                MATCH (s:Student {student_id: $student_id})
                MATCH (k:KnowledgePoint {knowledge_id: $knowledge_id})
                CREATE (s)-[:MADE_ERROR {
                    error_type: $error_type,
                    description: $description,
                    code_snippet: $code_snippet,
                    exercise_id: $exercise_id,
                    timestamp: datetime(),
                    resolved: false
                }]->(k)
            """, student_id=student_id, knowledge_id=knowledge_id, **error_data)

    def record_exercise_attempt(self, student_id: str, exercise_id: str,
                               success: bool, time_spent: int, attempts: int):
        """记录练习题尝试"""
        with self.driver.session() as session:
            session.run("""
                MATCH (s:Student {student_id: $student_id})
                MATCH (e:Exercise {exercise_id: $exercise_id})
                CREATE (s)-[:ATTEMPTED {
                    timestamp: datetime(),
                    success: $success,
                    time_spent: $time_spent,
                    attempts: $attempts
                }]->(e)
            """, student_id=student_id, exercise_id=exercise_id,
                       success=success, time_spent=time_spent, attempts=attempts)


def initialize_python_course():
    """初始化 Python 基础课程知识图谱"""

    # 从配置文件读取 Neo4j 连接信息
    initializer = Neo4jGraphInitializer(
        uri=config.NEO4J_URI,
        user=config.NEO4J_USER,
        password=config.NEO4J_PASSWORD
    )

    try:
        print("=" * 50)
        print("开始初始化 Neo4j 知识图谱")
        print("=" * 50)

        # 1. 创建约束和索引
        initializer.create_constraints_and_indexes()

        # 2. 创建课程
        course = {
            "course_id": "course_python_basics",
            "name": "Python 编程基础",
            "description": "从零开始学习Python编程",
            "level": "beginner"
        }
        initializer.create_course(course)

        # 3. 创建章节
        chapters = [
            {
                "chapter_id": "chapter_variables",
                "name": "变量与数据类型",
                "order": 1,
                "description": "学习Python的基本数据类型和变量声明"
            },
            {
                "chapter_id": "chapter_control_flow",
                "name": "控制流程",
                "order": 2,
                "description": "学习if语句、循环等控制结构"
            },
            {
                "chapter_id": "chapter_functions",
                "name": "函数",
                "order": 3,
                "description": "学习函数的定义和调用"
            }
        ]
        initializer.create_chapters(chapters)

        # 4. 创建知识点
        knowledge_points = [
            # 第一章知识点
            {
                "knowledge_id": "kp_variable_declaration",
                "name": "变量声明",
                "description": "理解变量的定义和赋值",
                "difficulty": 1,
                "prerequisites": []
            },
            {
                "knowledge_id": "kp_int_type",
                "name": "整数类型",
                "description": "理解int类型的使用",
                "difficulty": 1,
                "prerequisites": ["kp_variable_declaration"]
            },
            {
                "knowledge_id": "kp_float_type",
                "name": "浮点数类型",
                "description": "理解float类型的使用",
                "difficulty": 1,
                "prerequisites": ["kp_variable_declaration"]
            },
            {
                "knowledge_id": "kp_string_type",
                "name": "字符串类型",
                "description": "理解str类型和字符串操作",
                "difficulty": 2,
                "prerequisites": ["kp_variable_declaration"]
            },
            {
                "knowledge_id": "kp_type_conversion",
                "name": "类型转换",
                "description": "掌握不同数据类型之间的转换",
                "difficulty": 2,
                "prerequisites": ["kp_int_type", "kp_string_type"]
            },
            # 第二章知识点
            {
                "knowledge_id": "kp_comparison_operators",
                "name": "比较运算符",
                "description": "理解>、<、==等比较运算符",
                "difficulty": 2,
                "prerequisites": ["kp_int_type"]
            },
            {
                "knowledge_id": "kp_if_statement",
                "name": "if条件语句",
                "description": "理解条件判断的语法和逻辑",
                "difficulty": 2,
                "prerequisites": ["kp_comparison_operators"]
            },
            {
                "knowledge_id": "kp_elif_else",
                "name": "elif和else语句",
                "description": "理解多分支条件判断",
                "difficulty": 2,
                "prerequisites": ["kp_if_statement"]
            },
            {
                "knowledge_id": "kp_while_loop",
                "name": "while循环",
                "description": "理解while循环的语法和应用",
                "difficulty": 3,
                "prerequisites": ["kp_if_statement"]
            },
            {
                "knowledge_id": "kp_for_loop",
                "name": "for循环",
                "description": "理解for循环的语法和应用",
                "difficulty": 3,
                "prerequisites": ["kp_while_loop"]
            },
            # 第三章知识点
            {
                "knowledge_id": "kp_function_definition",
                "name": "函数定义",
                "description": "理解def关键字和函数定义语法",
                "difficulty": 3,
                "prerequisites": ["kp_variable_declaration"]
            },
            {
                "knowledge_id": "kp_function_parameters",
                "name": "函数参数",
                "description": "理解函数参数的传递和使用",
                "difficulty": 3,
                "prerequisites": ["kp_function_definition"]
            },
            {
                "knowledge_id": "kp_return_value",
                "name": "返回值",
                "description": "理解return语句和返回值",
                "difficulty": 3,
                "prerequisites": ["kp_function_definition"]
            }
        ]
        initializer.create_knowledge_points(knowledge_points)

        # 5. 创建练习题
        exercises = [
            {
                "exercise_id": "ex_var_001",
                "title": "创建第一个变量",
                "type": "coding",
                "difficulty": 1,
                "content": "声明一个变量x并赋值为10",
                "test_cases": json.dumps([{"input":"","expected":"x=10"}]),
                "estimated_time": 5
            },
            {
                "exercise_id": "ex_var_002",
                "title": "字符串变量",
                "type": "coding",
                "difficulty": 1,
                "content": "创建一个字符串变量name，赋值为你的名字",
                "test_cases": json.dumps([{"input":"","expected":"name='xxx'"}]),
                "estimated_time": 5
            },
            {
                "exercise_id": "ex_if_001",
                "title": "判断数字正负",
                "type": "coding",
                "difficulty": 2,
                "content": "编写if语句判断变量x是否大于0",
                "test_cases": json.dumps([
                    {"input":"x=5","expected":"正数"},
                    {"input":"x=-3","expected":"负数"}
                ]),
                "estimated_time": 10
            },
            {
                "exercise_id": "ex_loop_001",
                "title": "打印1到10",
                "type": "coding",
                "difficulty": 2,
                "content": "使用for循环打印1到10的所有数字",
                "test_cases": json.dumps([{"input":"","expected":"1\n2\n...\n10"}]),
                "estimated_time": 10
            },
            {
                "exercise_id": "ex_func_001",
                "title": "创建加法函数",
                "type": "coding",
                "difficulty": 3,
                "content": "定义一个函数add，接收两个参数并返回它们的和",
                "test_cases": json.dumps([
                    {"input":"add(2,3)","expected":"5"},
                    {"input":"add(10,20)","expected":"30"}
                ]),
                "estimated_time": 15
            }
        ]
        initializer.create_exercises(exercises)

        # 6. 关联课程和章节
        initializer.link_course_chapters("course_python_basics", [
            "chapter_variables",
            "chapter_control_flow",
            "chapter_functions"
        ])

        # 7. 关联章节和知识点
        chapter_knowledge_links = [
            {"chapter_id": "chapter_variables", "knowledge_id": "kp_variable_declaration", "order": 1},
            {"chapter_id": "chapter_variables", "knowledge_id": "kp_int_type", "order": 2},
            {"chapter_id": "chapter_variables", "knowledge_id": "kp_float_type", "order": 3},
            {"chapter_id": "chapter_variables", "knowledge_id": "kp_string_type", "order": 4},
            {"chapter_id": "chapter_variables", "knowledge_id": "kp_type_conversion", "order": 5},
            {"chapter_id": "chapter_control_flow", "knowledge_id": "kp_comparison_operators", "order": 1},
            {"chapter_id": "chapter_control_flow", "knowledge_id": "kp_if_statement", "order": 2},
            {"chapter_id": "chapter_control_flow", "knowledge_id": "kp_elif_else", "order": 3},
            {"chapter_id": "chapter_control_flow", "knowledge_id": "kp_while_loop", "order": 4},
            {"chapter_id": "chapter_control_flow", "knowledge_id": "kp_for_loop", "order": 5},
            {"chapter_id": "chapter_functions", "knowledge_id": "kp_function_definition", "order": 1},
            {"chapter_id": "chapter_functions", "knowledge_id": "kp_function_parameters", "order": 2},
            {"chapter_id": "chapter_functions", "knowledge_id": "kp_return_value", "order": 3}
        ]
        initializer.link_chapter_knowledge(chapter_knowledge_links)

        # 8. 创建知识点依赖关系
        prerequisite_links = [
            {"knowledge_id": "kp_int_type", "prerequisite_id": "kp_variable_declaration"},
            {"knowledge_id": "kp_float_type", "prerequisite_id": "kp_variable_declaration"},
            {"knowledge_id": "kp_string_type", "prerequisite_id": "kp_variable_declaration"},
            {"knowledge_id": "kp_type_conversion", "prerequisite_id": "kp_int_type"},
            {"knowledge_id": "kp_type_conversion", "prerequisite_id": "kp_string_type"},
            {"knowledge_id": "kp_comparison_operators", "prerequisite_id": "kp_int_type"},
            {"knowledge_id": "kp_if_statement", "prerequisite_id": "kp_comparison_operators"},
            {"knowledge_id": "kp_elif_else", "prerequisite_id": "kp_if_statement"},
            {"knowledge_id": "kp_while_loop", "prerequisite_id": "kp_if_statement"},
            {"knowledge_id": "kp_for_loop", "prerequisite_id": "kp_while_loop"},
            {"knowledge_id": "kp_function_parameters", "prerequisite_id": "kp_function_definition"},
            {"knowledge_id": "kp_return_value", "prerequisite_id": "kp_function_definition"}
        ]
        initializer.link_knowledge_prerequisites(prerequisite_links)

        # 9. 关联练习题和知识点
        exercise_knowledge_links = [
            {"exercise_id": "ex_var_001", "knowledge_id": "kp_variable_declaration", "weight": 1.0},
            {"exercise_id": "ex_var_001", "knowledge_id": "kp_int_type", "weight": 0.8},
            {"exercise_id": "ex_var_002", "knowledge_id": "kp_variable_declaration", "weight": 1.0},
            {"exercise_id": "ex_var_002", "knowledge_id": "kp_string_type", "weight": 1.0},
            {"exercise_id": "ex_if_001", "knowledge_id": "kp_if_statement", "weight": 1.0},
            {"exercise_id": "ex_if_001", "knowledge_id": "kp_comparison_operators", "weight": 0.8},
            {"exercise_id": "ex_loop_001", "knowledge_id": "kp_for_loop", "weight": 1.0},
            {"exercise_id": "ex_func_001", "knowledge_id": "kp_function_definition", "weight": 1.0},
            {"exercise_id": "ex_func_001", "knowledge_id": "kp_function_parameters", "weight": 1.0},
            {"exercise_id": "ex_func_001", "knowledge_id": "kp_return_value", "weight": 1.0}
        ]
        initializer.link_exercise_knowledge(exercise_knowledge_links)

        print("=" * 50)
        print("✅ Neo4j 知识图谱初始化完成！")
        print("=" * 50)

    finally:
        initializer.close()


if __name__ == "__main__":
    # 从配置文件读取 Neo4j 连接信息
    print("正在使用配置文件中的 Neo4j 连接信息...")
    initialize_python_course()
