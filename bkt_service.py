"""
BKT 服务层 - 与 Neo4j 和业务逻辑集成
提供完整的知识追踪功能 API
"""

import redis
from typing import List, Dict, Optional, Tuple
from datetime import datetime
from neo4j import GraphDatabase

from bkt_algorithm import BKTEngine, BKTParameters, MasteryState, AdaptiveBKTEngine
from config import config


class BKTService:
    """BKT 服务 - 整合 Redis 缓存和 Neo4j 图谱"""

    def __init__(self, neo4j_uri: str = None, neo4j_user: str = None,
                 neo4j_password: str = None, redis_host: str = None,
                 redis_port: int = None, redis_password: str = None):
        """
        初始化 BKT 服务

        Args:
            neo4j_uri: Neo4j 连接地址（可选，默认从配置读取）
            neo4j_user: Neo4j 用户名（可选，默认从配置读取）
            neo4j_password: Neo4j 密码（可选，默认从配置读取）
            redis_host: Redis 主机（可选，默认从配置读取）
            redis_port: Redis 端口（可选，默认从配置读取）
            redis_password: Redis 密码（可选，默认从配置读取）
        """
        # 使用传入参数或从配置读取
        neo4j_uri = neo4j_uri or config.NEO4J_URI
        neo4j_user = neo4j_user or config.NEO4J_USER
        neo4j_password = neo4j_password or config.NEO4J_PASSWORD
        redis_host = redis_host or config.REDIS_HOST
        redis_port = redis_port or config.REDIS_PORT
        redis_password = redis_password or config.REDIS_PASSWORD

        # Neo4j 连接
        self.neo4j_driver = GraphDatabase.driver(
            neo4j_uri,
            auth=(neo4j_user, neo4j_password)
        )

        # Redis 连接
        redis_kwargs = {
            'host': redis_host,
            'port': redis_port,
            'db': config.REDIS_DB,
            'decode_responses': False
        }
        if redis_password:
            redis_kwargs['password'] = redis_password

        self.redis_client = redis.Redis(**redis_kwargs)

        # BKT 引擎：参数统一从配置层读取
        bkt_parameters = BKTParameters(
            p_init=config.BKT_P_INIT,
            p_learn=config.BKT_P_LEARN,
            p_slip=config.BKT_P_SLIP,
            p_guess=config.BKT_P_GUESS,
        )
        self.bkt_engine = AdaptiveBKTEngine(
            self.redis_client, default_parameters=bkt_parameters
        )

    def close(self):
        """关闭所有连接"""
        self.neo4j_driver.close()
        self.redis_client.close()

    def sync_mastery_to_neo4j(self, state: MasteryState):
        """
        将掌握度同步到 Neo4j 图谱

        Args:
            state: 掌握状态对象
        """
        with self.neo4j_driver.session() as session:
            session.run("""
                MATCH (s:Student {student_id: $student_id})
                MATCH (k:KnowledgePoint {knowledge_id: $knowledge_id})
                MERGE (s)-[m:MASTERY]->(k)
                SET m.level = $level,
                    m.attempts = $attempts,
                    m.correct_count = $correct_count,
                    m.error_count = $error_count,
                    m.last_updated = datetime($last_updated),
                    m.learning_velocity = $learning_velocity
            """,
                student_id=state.student_id,
                knowledge_id=state.knowledge_id,
                level=state.mastery_level,
                attempts=state.attempts,
                correct_count=state.correct_count,
                error_count=state.error_count,
                last_updated=state.last_updated,
                learning_velocity=state.learning_velocity
            )

    def get_exercise_knowledge_points(self, exercise_id: str) -> List[Tuple[str, float]]:
        """
        获取练习题覆盖的知识点及权重

        Args:
            exercise_id: 练习题ID

        Returns:
            List[Tuple[str, float]]: [(knowledge_id, weight), ...]
        """
        with self.neo4j_driver.session() as session:
            result = session.run("""
                MATCH (e:Exercise {exercise_id: $exercise_id})-[c:COVERS]->(k:KnowledgePoint)
                RETURN k.knowledge_id as knowledge_id, c.weight as weight
            """, exercise_id=exercise_id)

            return [(record["knowledge_id"], record["weight"])
                    for record in result]

    def record_exercise_submission(self, student_id: str, exercise_id: str,
                                  is_correct: bool, code_snippet: str = "",
                                  error_type: str = None,
                                  error_description: str = None) -> Dict[str, MasteryState]:
        """
        记录学生练习题提交，更新相关知识点掌握度

        Args:
            student_id: 学生ID
            exercise_id: 练习题ID
            is_correct: 是否答对
            code_snippet: 学生提交的代码
            error_type: 错误类型（syntax/logic/concept）
            error_description: 错误描述

        Returns:
            Dict[str, MasteryState]: {knowledge_id: MasteryState} 更新后的掌握状态
        """
        # 1. 获取练习题覆盖的知识点
        knowledge_points = self.get_exercise_knowledge_points(exercise_id)

        if not knowledge_points:
            raise ValueError(f"练习题 {exercise_id} 未关联任何知识点")

        # 2. 更新每个知识点的掌握度（根据权重）
        updated_states = {}

        for knowledge_id, weight in knowledge_points:
            # 权重高的知识点，答对/答错的影响更大
            # 这里通过调整 BKT 参数来体现权重影响
            if weight >= 0.8:
                # 主要知识点：使用默认参数
                state = self.bkt_engine.update_mastery(
                    student_id, knowledge_id, is_correct
                )
            else:
                # 次要知识点：降低学习转化率（影响较小）
                custom_params = BKTParameters(
                    p_init=0.0,
                    p_learn=0.15,  # 降低学习转化率
                    p_slip=0.1,
                    p_guess=0.2
                )
                state = self.bkt_engine.update_mastery(
                    student_id, knowledge_id, is_correct, custom_params
                )

            updated_states[knowledge_id] = state

            # 同步到 Neo4j
            self.sync_mastery_to_neo4j(state)

        # 3. 如果答错，记录错误到 Neo4j
        if not is_correct and error_type:
            self._record_error_to_neo4j(
                student_id, knowledge_points[0][0],  # 主要知识点
                exercise_id, error_type, error_description, code_snippet
            )

        # 4. 记录练习尝试到 Neo4j
        self._record_attempt_to_neo4j(
            student_id, exercise_id, is_correct, len(knowledge_points)
        )

        return updated_states

    def _record_error_to_neo4j(self, student_id: str, knowledge_id: str,
                               exercise_id: str, error_type: str,
                               description: str, code_snippet: str):
        """记录错误到 Neo4j"""
        with self.neo4j_driver.session() as session:
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
            """,
                student_id=student_id,
                knowledge_id=knowledge_id,
                error_type=error_type,
                description=description,
                code_snippet=code_snippet,
                exercise_id=exercise_id
            )

    def _record_attempt_to_neo4j(self, student_id: str, exercise_id: str,
                                 success: bool, knowledge_count: int):
        """记录练习尝试到 Neo4j"""
        with self.neo4j_driver.session() as session:
            session.run("""
                MATCH (s:Student {student_id: $student_id})
                MATCH (e:Exercise {exercise_id: $exercise_id})
                CREATE (s)-[:ATTEMPTED {
                    timestamp: datetime(),
                    success: $success,
                    time_spent: 0,
                    attempts: 1
                }]->(e)
            """,
                student_id=student_id,
                exercise_id=exercise_id,
                success=success
            )

    def get_student_weak_points(self, student_id: str,
                               threshold: float = 0.6,
                               limit: int = 5) -> List[Dict]:
        """
        获取学生的薄弱知识点（整合 Neo4j 和 Redis）

        Args:
            student_id: 学生ID
            threshold: 掌握度阈值
            limit: 返回数量

        Returns:
            List[Dict]: 薄弱知识点详情列表
        """
        # 1. 从 Neo4j 获取学生已学习的知识点
        with self.neo4j_driver.session() as session:
            result = session.run("""
                MATCH (s:Student {student_id: $student_id})-[m:MASTERY]->(k:KnowledgePoint)
                WHERE m.level < $threshold
                RETURN k.knowledge_id as knowledge_id,
                       k.name as name,
                       k.description as description,
                       k.difficulty as difficulty,
                       m.level as mastery_level,
                       m.error_count as error_count
                ORDER BY m.level ASC
                LIMIT $limit
            """, student_id=student_id, threshold=threshold, limit=limit)

            weak_points = []
            for record in result:
                weak_points.append({
                    "knowledge_id": record["knowledge_id"],
                    "name": record["name"],
                    "description": record["description"],
                    "difficulty": record["difficulty"],
                    "mastery_level": record["mastery_level"],
                    "error_count": record["error_count"]
                })

            return weak_points

    def get_student_error_distribution(self, student_id: str) -> Dict[str, int]:
        """
        获取学生的错误类型分布

        Args:
            student_id: 学生ID

        Returns:
            Dict[str, int]: {"syntax": 5, "logic": 3, "concept": 2}
        """
        with self.neo4j_driver.session() as session:
            result = session.run("""
                MATCH (s:Student {student_id: $student_id})-[e:MADE_ERROR]->(:KnowledgePoint)
                RETURN e.error_type as error_type, COUNT(*) as count
            """, student_id=student_id)

            distribution = {}
            for record in result:
                distribution[record["error_type"]] = record["count"]

            return distribution

    def get_mastery_radar_data(self, student_id: str,
                              chapter_id: str = None) -> Dict:
        """
        获取知识点掌握度雷达图数据

        Args:
            student_id: 学生ID
            chapter_id: 章节ID（可选，不传则返回所有）

        Returns:
            Dict: 雷达图数据
        """
        query = """
            MATCH (s:Student {student_id: $student_id})-[m:MASTERY]->(k:KnowledgePoint)
        """

        if chapter_id:
            query += """
            MATCH (ch:Chapter {chapter_id: $chapter_id})-[:INCLUDES]->(k)
            """

        query += """
            RETURN k.name as name,
                   k.knowledge_id as knowledge_id,
                   m.level as mastery_level
            ORDER BY k.knowledge_id
        """

        with self.neo4j_driver.session() as session:
            result = session.run(query, student_id=student_id, chapter_id=chapter_id)

            labels = []
            values = []

            for record in result:
                labels.append(record["name"])
                values.append(round(record["mastery_level"], 2))

            return {
                "labels": labels,
                "values": values,
                "max_value": 1.0
            }

    def predict_exercise_success(self, student_id: str,
                                exercise_id: str) -> Dict:
        """
        预测学生完成该练习题的成功概率

        Args:
            student_id: 学生ID
            exercise_id: 练习题ID

        Returns:
            Dict: 预测结果
        """
        # 获取练习题覆盖的知识点
        knowledge_points = self.get_exercise_knowledge_points(exercise_id)

        if not knowledge_points:
            return {
                "success_probability": 0.5,
                "knowledge_breakdown": []
            }

        # 计算加权平均成功概率
        total_weight = sum(weight for _, weight in knowledge_points)
        weighted_prob = 0.0

        breakdown = []
        for knowledge_id, weight in knowledge_points:
            prob = self.bkt_engine.predict_success_probability(student_id, knowledge_id)
            weighted_prob += prob * (weight / total_weight)

            state = self.bkt_engine.get_mastery_state(student_id, knowledge_id)
            breakdown.append({
                "knowledge_id": knowledge_id,
                "mastery_level": state.mastery_level,
                "success_probability": prob,
                "weight": weight
            })

        return {
            "success_probability": round(weighted_prob, 3),
            "knowledge_breakdown": breakdown
        }

    def get_learning_progress_report(self, student_id: str,
                                    course_id: str) -> Dict:
        """
        生成学生学习进度报告

        Args:
            student_id: 学生ID
            course_id: 课程ID

        Returns:
            Dict: 进度报告
        """
        with self.neo4j_driver.session() as session:
            # 获取课程所有知识点
            result = session.run("""
                MATCH (course:Course {course_id: $course_id})-[:CONTAINS]->(ch:Chapter)
                      -[:INCLUDES]->(k:KnowledgePoint)
                OPTIONAL MATCH (s:Student {student_id: $student_id})-[m:MASTERY]->(k)
                RETURN k.knowledge_id as knowledge_id,
                       k.name as name,
                       k.difficulty as difficulty,
                       COALESCE(m.level, 0.0) as mastery_level,
                       COALESCE(m.attempts, 0) as attempts
                ORDER BY k.difficulty, k.knowledge_id
            """, student_id=student_id, course_id=course_id)

            knowledge_points = []
            total_mastery = 0.0
            learned_count = 0

            for record in result:
                kp_data = {
                    "knowledge_id": record["knowledge_id"],
                    "name": record["name"],
                    "difficulty": record["difficulty"],
                    "mastery_level": record["mastery_level"],
                    "attempts": record["attempts"],
                    "status": "未学习" if record["attempts"] == 0
                             else ("已掌握" if record["mastery_level"] >= 0.7
                                  else "学习中")
                }
                knowledge_points.append(kp_data)

                total_mastery += record["mastery_level"]
                if record["attempts"] > 0:
                    learned_count += 1

            total_count = len(knowledge_points)
            overall_progress = (total_mastery / total_count * 100) if total_count > 0 else 0

            return {
                "student_id": student_id,
                "course_id": course_id,
                "total_knowledge_points": total_count,
                "learned_count": learned_count,
                "overall_progress": round(overall_progress, 1),
                "average_mastery": round(total_mastery / total_count, 3) if total_count > 0 else 0,
                "knowledge_points": knowledge_points
            }


# ========================================
# FastAPI 路由示例（可选）
# ========================================

"""
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

app = FastAPI()

# 使用配置文件初始化服务
bkt_service = BKTService()


class ExerciseSubmission(BaseModel):
    student_id: str
    exercise_id: str
    is_correct: bool
    code_snippet: str = ""
    error_type: str = None
    error_description: str = None


@app.post("/api/exercise/submit")
async def submit_exercise(submission: ExerciseSubmission):
    try:
        updated_states = bkt_service.record_exercise_submission(
            submission.student_id,
            submission.exercise_id,
            submission.is_correct,
            submission.code_snippet,
            submission.error_type,
            submission.error_description
        )

        return {
            "success": True,
            "updated_knowledge_points": {
                kid: {
                    "mastery_level": state.mastery_level,
                    "attempts": state.attempts
                }
                for kid, state in updated_states.items()
            }
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/api/student/{student_id}/weak-points")
async def get_weak_points(student_id: str, threshold: float = 0.6, limit: int = 5):
    weak_points = bkt_service.get_student_weak_points(student_id, threshold, limit)
    return {"weak_points": weak_points}


@app.get("/api/student/{student_id}/error-distribution")
async def get_error_distribution(student_id: str):
    distribution = bkt_service.get_student_error_distribution(student_id)
    return {"error_distribution": distribution}


@app.get("/api/student/{student_id}/mastery-radar")
async def get_mastery_radar(student_id: str, chapter_id: str = None):
    radar_data = bkt_service.get_mastery_radar_data(student_id, chapter_id)
    return {"radar_data": radar_data}


@app.get("/api/exercise/{exercise_id}/predict")
async def predict_success(exercise_id: str, student_id: str):
    prediction = bkt_service.predict_exercise_success(student_id, exercise_id)
    return {"prediction": prediction}


@app.get("/api/student/{student_id}/progress/{course_id}")
async def get_progress_report(student_id: str, course_id: str):
    report = bkt_service.get_learning_progress_report(student_id, course_id)
    return {"report": report}
"""
