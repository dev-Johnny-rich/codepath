"""
推荐引擎 - 基于知识图谱和BKT掌握度推荐下一步学习任务
支持多种推荐策略：薄弱知识点强化、循序渐进、个性化难度匹配
"""

from typing import List, Dict, Optional, Tuple
from dataclasses import dataclass
from enum import Enum
from neo4j import GraphDatabase
import redis

from bkt_service import BKTService
from config import config


class RecommendationStrategy(Enum):
    """推荐策略枚举"""
    WEAKNESS_FIRST = "weakness_first"      # 优先强化薄弱知识点
    PROGRESSIVE = "progressive"            # 循序渐进（按课程顺序）
    DIFFICULTY_ADAPTIVE = "difficulty_adaptive"  # 自适应难度
    PREREQUISITE_AWARE = "prerequisite_aware"    # 考虑前置依赖


@dataclass
class ExerciseRecommendation:
    """练习题推荐结果"""
    exercise_id: str
    title: str
    difficulty: int
    estimated_time: int
    target_knowledge_points: List[str]
    reason: str                    # 推荐理由
    success_probability: float     # 预测成功概率
    priority_score: float          # 优先级分数


@dataclass
class KnowledgePathRecommendation:
    """知识点学习路径推荐"""
    knowledge_id: str
    name: str
    description: str
    difficulty: int
    current_mastery: float
    prerequisites_ready: bool
    recommended_exercises: List[str]
    priority_score: float


class RecommendationEngine:
    """推荐引擎"""

    def __init__(self, neo4j_uri: str = None, neo4j_user: str = None,
                 neo4j_password: str = None, redis_host: str = None,
                 redis_port: int = None, redis_password: str = None):
        """
        初始化推荐引擎

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

        self.neo4j_driver = GraphDatabase.driver(
            neo4j_uri,
            auth=(neo4j_user, neo4j_password)
        )

        redis_kwargs = {
            'host': redis_host,
            'port': redis_port,
            'db': config.REDIS_DB,
            'decode_responses': True
        }
        if redis_password:
            redis_kwargs['password'] = redis_password

        self.redis_client = redis.Redis(**redis_kwargs)

        self.bkt_service = BKTService(
            neo4j_uri, neo4j_user, neo4j_password,
            redis_host, redis_port, redis_password
        )

    def close(self):
        """关闭所有连接"""
        self.neo4j_driver.close()
        self.redis_client.close()
        self.bkt_service.close()

    def _get_cache_key(self, student_id: str, strategy: str) -> str:
        """生成推荐结果缓存键"""
        return f"recommend:exercises:{student_id}:{strategy}"

    def recommend_exercises(self, student_id: str,
                          strategy: RecommendationStrategy = RecommendationStrategy.WEAKNESS_FIRST,
                          limit: int = 5,
                          use_cache: bool = True) -> List[ExerciseRecommendation]:
        """
        推荐练习题

        Args:
            student_id: 学生ID
            strategy: 推荐策略
            limit: 推荐数量
            use_cache: 是否使用缓存

        Returns:
            List[ExerciseRecommendation]: 推荐结果列表
        """
        # 检查缓存
        cache_key = self._get_cache_key(student_id, strategy.value)
        if use_cache:
            cached = self.redis_client.get(cache_key)
            if cached:
                import json
                return [ExerciseRecommendation(**item)
                       for item in json.loads(cached)]

        # 根据策略选择推荐方法
        if strategy == RecommendationStrategy.WEAKNESS_FIRST:
            recommendations = self._recommend_by_weakness(student_id, limit)
        elif strategy == RecommendationStrategy.PROGRESSIVE:
            recommendations = self._recommend_progressive(student_id, limit)
        elif strategy == RecommendationStrategy.DIFFICULTY_ADAPTIVE:
            recommendations = self._recommend_adaptive_difficulty(student_id, limit)
        elif strategy == RecommendationStrategy.PREREQUISITE_AWARE:
            recommendations = self._recommend_prerequisite_aware(student_id, limit)
        else:
            recommendations = self._recommend_by_weakness(student_id, limit)

        # 缓存结果（1小时）
        import json
        self.redis_client.setex(
            cache_key,
            3600,
            json.dumps([vars(r) for r in recommendations])
        )

        return recommendations

    def _recommend_by_weakness(self, student_id: str, limit: int) -> List[ExerciseRecommendation]:
        """
        策略1：优先推荐强化薄弱知识点的练习题

        逻辑：
        1. 找出掌握度 < 0.6 且错误次数 > 2 的知识点
        2. 推荐覆盖这些知识点的练习题
        3. 优先推荐学生未做过的题目
        """
        with self.neo4j_driver.session() as session:
            result = session.run("""
                // 1. 找出薄弱知识点
                MATCH (s:Student {student_id: $student_id})-[m:MASTERY]->(k:KnowledgePoint)
                WHERE m.level < 0.6 AND m.error_count >= 2
                WITH k, m.level as mastery, m.error_count as errors
                ORDER BY mastery ASC, errors DESC
                LIMIT 3

                // 2. 找到覆盖这些知识点的练习题
                MATCH (e:Exercise)-[c:COVERS]->(k)
                WHERE NOT (s)-[:ATTEMPTED]->(e)  // 未做过

                // 3. 计算优先级分数
                WITH e, k, mastery, c.weight as weight,
                     (1 - mastery) * c.weight * 10 as priority

                RETURN DISTINCT e.exercise_id as exercise_id,
                       e.title as title,
                       e.difficulty as difficulty,
                       e.estimated_time as estimated_time,
                       COLLECT(DISTINCT k.knowledge_id) as knowledge_ids,
                       AVG(priority) as priority_score,
                       AVG(mastery) as avg_mastery
                ORDER BY priority_score DESC
                LIMIT $limit
            """, student_id=student_id, limit=limit)

            recommendations = []
            for record in result:
                # 预测成功概率
                prediction = self.bkt_service.predict_exercise_success(
                    student_id,
                    record["exercise_id"]
                )

                recommendations.append(ExerciseRecommendation(
                    exercise_id=record["exercise_id"],
                    title=record["title"],
                    difficulty=record["difficulty"],
                    estimated_time=record["estimated_time"],
                    target_knowledge_points=record["knowledge_ids"],
                    reason=f"强化薄弱知识点 (平均掌握度: {record['avg_mastery']:.0%})",
                    success_probability=prediction["success_probability"],
                    priority_score=record["priority_score"]
                ))

            return recommendations

    def _recommend_progressive(self, student_id: str, limit: int) -> List[ExerciseRecommendation]:
        """
        策略2：循序渐进推荐（按课程顺序）

        逻辑：
        1. 找到学生正在学习的章节
        2. 按知识点顺序推荐练习题
        3. 确保前置知识已掌握
        """
        with self.neo4j_driver.session() as session:
            result = session.run("""
                // 1. 找到学生最近学习的章节
                MATCH (s:Student {student_id: $student_id})-[:ATTEMPTED]->(e:Exercise)
                      -[:COVERS]->(k:KnowledgePoint)<-[:INCLUDES]-(ch:Chapter)
                WITH ch, MAX(e.exercise_id) as last_exercise
                ORDER BY last_exercise DESC
                LIMIT 1

                // 2. 获取该章节的知识点（按顺序）
                MATCH (ch)-[inc:INCLUDES]->(k:KnowledgePoint)
                OPTIONAL MATCH (s)-[m:MASTERY]->(k)

                // 3. 过滤：掌握度 < 0.8 或未学习的知识点
                WHERE m IS NULL OR m.level < 0.8
                WITH k, COALESCE(m.level, 0) as mastery, inc.order as order
                ORDER BY order ASC
                LIMIT 3

                // 4. 推荐相关练习题
                MATCH (e:Exercise)-[:COVERS]->(k)
                WHERE NOT (s)-[:ATTEMPTED]->(e)

                RETURN DISTINCT e.exercise_id as exercise_id,
                       e.title as title,
                       e.difficulty as difficulty,
                       e.estimated_time as estimated_time,
                       COLLECT(DISTINCT k.knowledge_id) as knowledge_ids,
                       MIN(order) as min_order
                ORDER BY min_order ASC, e.difficulty ASC
                LIMIT $limit
            """, student_id=student_id, limit=limit)

            recommendations = []
            for record in result:
                prediction = self.bkt_service.predict_exercise_success(
                    student_id,
                    record["exercise_id"]
                )

                recommendations.append(ExerciseRecommendation(
                    exercise_id=record["exercise_id"],
                    title=record["title"],
                    difficulty=record["difficulty"],
                    estimated_time=record["estimated_time"],
                    target_knowledge_points=record["knowledge_ids"],
                    reason="按课程顺序推进",
                    success_probability=prediction["success_probability"],
                    priority_score=100 - record["min_order"]  # 顺序靠前优先级高
                ))

            return recommendations

    def _recommend_adaptive_difficulty(self, student_id: str, limit: int) -> List[ExerciseRecommendation]:
        """
        策略3：自适应难度推荐

        逻辑：
        1. 计算学生的整体能力水平
        2. 推荐难度匹配的练习题（能力 ± 1 难度级别）
        3. 根据最近表现动态调整
        """
        # 1. 计算学生能力水平
        with self.neo4j_driver.session() as session:
            # 获取学生最近10次练习的成功率
            recent_performance = session.run("""
                MATCH (s:Student {student_id: $student_id})-[a:ATTEMPTED]->(e:Exercise)
                WITH a.success as success, e.difficulty as difficulty
                ORDER BY a.timestamp DESC
                LIMIT 10
                RETURN AVG(CASE WHEN success THEN 1.0 ELSE 0.0 END) as success_rate,
                       AVG(difficulty) as avg_difficulty
            """, student_id=student_id).single()

            if recent_performance:
                success_rate = recent_performance["success_rate"] or 0.5
                avg_difficulty = recent_performance["avg_difficulty"] or 2
            else:
                success_rate = 0.5
                avg_difficulty = 1

            # 根据成功率调整目标难度
            if success_rate > 0.8:
                target_difficulty = min(avg_difficulty + 1, 5)
            elif success_rate < 0.4:
                target_difficulty = max(avg_difficulty - 1, 1)
            else:
                target_difficulty = avg_difficulty

            # 2. 推荐匹配难度的练习题
            result = session.run("""
                MATCH (s:Student {student_id: $student_id})
                MATCH (e:Exercise)
                WHERE NOT (s)-[:ATTEMPTED]->(e)
                  AND ABS(e.difficulty - $target_difficulty) <= 1

                // 获取覆盖的知识点
                MATCH (e)-[:COVERS]->(k:KnowledgePoint)
                OPTIONAL MATCH (s)-[m:MASTERY]->(k)

                WITH e, COLLECT(k.knowledge_id) as knowledge_ids,
                     AVG(COALESCE(m.level, 0)) as avg_mastery,
                     ABS(e.difficulty - $target_difficulty) as diff_distance

                // 优先推荐掌握度中等的题目（有挑战但不太难）
                WHERE avg_mastery >= 0.4 AND avg_mastery <= 0.7

                RETURN e.exercise_id as exercise_id,
                       e.title as title,
                       e.difficulty as difficulty,
                       e.estimated_time as estimated_time,
                       knowledge_ids,
                       avg_mastery,
                       (1 - diff_distance * 0.2) * (1 - ABS(avg_mastery - 0.6)) * 10 as priority_score
                ORDER BY priority_score DESC
                LIMIT $limit
            """, student_id=student_id, target_difficulty=target_difficulty, limit=limit)

            recommendations = []
            for record in result:
                prediction = self.bkt_service.predict_exercise_success(
                    student_id,
                    record["exercise_id"]
                )

                reason = f"匹配你的当前水平 (难度: {record['difficulty']}, 预期成功率: {success_rate:.0%})"

                recommendations.append(ExerciseRecommendation(
                    exercise_id=record["exercise_id"],
                    title=record["title"],
                    difficulty=record["difficulty"],
                    estimated_time=record["estimated_time"],
                    target_knowledge_points=record["knowledge_ids"],
                    reason=reason,
                    success_probability=prediction["success_probability"],
                    priority_score=record["priority_score"]
                ))

            return recommendations

    def _recommend_prerequisite_aware(self, student_id: str, limit: int) -> List[ExerciseRecommendation]:
        """
        策略4：前置依赖感知推荐

        逻辑：
        1. 找出前置知识已掌握 (> 0.7) 的新知识点
        2. 推荐这些知识点的练习题
        3. 确保学习路径合理
        """
        with self.neo4j_driver.session() as session:
            result = session.run("""
                // 1. 找出学生尚未掌握的知识点
                MATCH (s:Student {student_id: $student_id})
                MATCH (k:KnowledgePoint)
                WHERE NOT (s)-[:MASTERY]->(k) OR
                      [(s)-[m:MASTERY]->(k) | m.level][0] < 0.7

                // 2. 检查前置依赖
                OPTIONAL MATCH (k)-[:REQUIRES]->(prereq:KnowledgePoint)
                WITH s, k, COLLECT(prereq) as prerequisites

                // 3. 确保所有前置知识已掌握
                WHERE ALL(p IN prerequisites WHERE
                    EXISTS((s)-[m:MASTERY]->(p)) AND
                    [(s)-[m:MASTERY]->(p) | m.level][0] > 0.7
                )

                // 4. 获取相关练习题
                MATCH (e:Exercise)-[:COVERS]->(k)
                WHERE NOT (s)-[:ATTEMPTED]->(e)

                WITH e, k, SIZE(prerequisites) as prereq_count

                RETURN DISTINCT e.exercise_id as exercise_id,
                       e.title as title,
                       e.difficulty as difficulty,
                       e.estimated_time as estimated_time,
                       COLLECT(DISTINCT k.knowledge_id) as knowledge_ids,
                       (10 - prereq_count) as priority_score
                ORDER BY priority_score DESC, e.difficulty ASC
                LIMIT $limit
            """, student_id=student_id, limit=limit)

            recommendations = []
            for record in result:
                prediction = self.bkt_service.predict_exercise_success(
                    student_id,
                    record["exercise_id"]
                )

                recommendations.append(ExerciseRecommendation(
                    exercise_id=record["exercise_id"],
                    title=record["title"],
                    difficulty=record["difficulty"],
                    estimated_time=record["estimated_time"],
                    target_knowledge_points=record["knowledge_ids"],
                    reason="前置知识已具备，可以学习新内容",
                    success_probability=prediction["success_probability"],
                    priority_score=record["priority_score"]
                ))

            return recommendations

    def recommend_learning_path(self, student_id: str,
                               course_id: str,
                               limit: int = 10) -> List[KnowledgePathRecommendation]:
        """
        推荐学习路径（知识点级别）

        Args:
            student_id: 学生ID
            course_id: 课程ID
            limit: 推荐数量

        Returns:
            List[KnowledgePathRecommendation]: 推荐的学习路径
        """
        with self.neo4j_driver.session() as session:
            result = session.run("""
                // 1. 获取课程所有知识点
                MATCH (course:Course {course_id: $course_id})-[:CONTAINS]->(ch:Chapter)
                      -[inc:INCLUDES]->(k:KnowledgePoint)

                // 2. 获取学生当前掌握度
                OPTIONAL MATCH (s:Student {student_id: $student_id})-[m:MASTERY]->(k)

                // 3. 过滤未掌握的知识点
                WHERE m IS NULL OR m.level < 0.8

                // 4. 检查前置依赖
                OPTIONAL MATCH (k)-[:REQUIRES]->(prereq:KnowledgePoint)
                WITH s, k, inc.order as chapter_order,
                     COALESCE(m.level, 0) as current_mastery,
                     COLLECT(prereq) as prerequisites

                // 5. 判断前置条件是否满足
                WITH k, chapter_order, current_mastery, prerequisites,
                     ALL(p IN prerequisites WHERE
                         EXISTS((s)-[pm:MASTERY]->(p)) AND
                         [(s)-[pm:MASTERY]->(p) | pm.level][0] > 0.7
                     ) as prereqs_ready

                // 6. 获取推荐练习题
                OPTIONAL MATCH (e:Exercise)-[:COVERS]->(k)
                WITH k, chapter_order, current_mastery, prereqs_ready,
                     COLLECT(DISTINCT e.exercise_id)[0..3] as exercises,
                     (CASE WHEN prereqs_ready THEN 10 ELSE 0 END) +
                     (1 - current_mastery) * 5 as priority

                RETURN k.knowledge_id as knowledge_id,
                       k.name as name,
                       k.description as description,
                       k.difficulty as difficulty,
                       current_mastery,
                       prereqs_ready,
                       exercises,
                       priority
                ORDER BY prereqs_ready DESC, priority DESC, chapter_order ASC
                LIMIT $limit
            """, student_id=student_id, course_id=course_id, limit=limit)

            path_recommendations = []
            for record in result:
                path_recommendations.append(KnowledgePathRecommendation(
                    knowledge_id=record["knowledge_id"],
                    name=record["name"],
                    description=record["description"],
                    difficulty=record["difficulty"],
                    current_mastery=record["current_mastery"],
                    prerequisites_ready=record["prereqs_ready"],
                    recommended_exercises=record["exercises"],
                    priority_score=record["priority"]
                ))

            return path_recommendations

    def get_next_best_action(self, student_id: str) -> Dict:
        """
        智能决策：为学生推荐下一步最佳学习行为

        综合考虑：
        1. 学习状态（刚开始 vs 进行中 vs 接近完成）
        2. 最近表现（连续答对 vs 频繁出错）
        3. 时间偏好（快速练习 vs 深度学习）

        Returns:
            Dict: {
                "action": "practice" | "review" | "advance" | "rest",
                "exercises": [...],
                "reason": "..."
            }
        """
        with self.neo4j_driver.session() as session:
            # 分析学生最近状态
            stats = session.run("""
                MATCH (s:Student {student_id: $student_id})-[a:ATTEMPTED]->(e:Exercise)
                WITH a.success as success, a.timestamp as timestamp
                ORDER BY timestamp DESC
                LIMIT 10
                RETURN COUNT(*) as recent_attempts,
                       SUM(CASE WHEN success THEN 1 ELSE 0 END) as recent_success,
                       AVG(CASE WHEN success THEN 1.0 ELSE 0.0 END) as success_rate
            """, student_id=student_id).single()

            if not stats or stats["recent_attempts"] == 0:
                # 新学生：从简单的开始
                exercises = self._recommend_progressive(student_id, 3)
                return {
                    "action": "start_learning",
                    "exercises": [vars(e) for e in exercises],
                    "reason": "欢迎开始学习！这里是为你准备的入门练习"
                }

            success_rate = stats["success_rate"]
            recent_attempts = stats["recent_attempts"]

            if success_rate >= 0.8:
                # 表现优秀：推进到新知识点
                exercises = self._recommend_prerequisite_aware(student_id, 3)
                return {
                    "action": "advance",
                    "exercises": [vars(e) for e in exercises],
                    "reason": "你做得很好！准备学习新内容了"
                }
            elif success_rate >= 0.5:
                # 表现中等：继续练习当前水平
                exercises = self._recommend_adaptive_difficulty(student_id, 3)
                return {
                    "action": "practice",
                    "exercises": [vars(e) for e in exercises],
                    "reason": "继续保持！多做几道练习巩固一下"
                }
            else:
                # 表现不佳：复习薄弱知识点
                exercises = self._recommend_by_weakness(student_id, 3)
                return {
                    "action": "review",
                    "exercises": [vars(e) for e in exercises],
                    "reason": "让我们先复习一下基础知识点"
                }


# ========================================
# 使用示例
# ========================================

def example_usage():
    """推荐引擎使用示例"""
    # 使用配置文件初始化
    engine = RecommendationEngine()

    try:
        student_id = "student_001"

        print("=" * 60)
        print("推荐引擎演示")
        print("=" * 60)

        # 1. 薄弱知识点推荐
        print("\n【策略1：强化薄弱知识点】")
        recommendations = engine.recommend_exercises(
            student_id,
            RecommendationStrategy.WEAKNESS_FIRST,
            limit=3
        )
        for i, rec in enumerate(recommendations, 1):
            print(f"\n{i}. {rec.title}")
            print(f"   难度: {'★' * rec.difficulty}")
            print(f"   目标知识点: {', '.join(rec.target_knowledge_points[:2])}")
            print(f"   预测成功率: {rec.success_probability:.0%}")
            print(f"   推荐理由: {rec.reason}")

        # 2. 循序渐进推荐
        print("\n" + "=" * 60)
        print("【策略2：循序渐进】")
        recommendations = engine.recommend_exercises(
            student_id,
            RecommendationStrategy.PROGRESSIVE,
            limit=3
        )
        for i, rec in enumerate(recommendations, 1):
            print(f"\n{i}. {rec.title}")
            print(f"   {rec.reason}")

        # 3. 智能决策
        print("\n" + "=" * 60)
        print("【智能决策：下一步最佳行动】")
        next_action = engine.get_next_best_action(student_id)
        print(f"\n建议行动: {next_action['action']}")
        print(f"理由: {next_action['reason']}")
        print(f"推荐练习数: {len(next_action['exercises'])}")

    finally:
        engine.close()


if __name__ == "__main__":
    example_usage()
