"""Bayesian Knowledge Tracing (BKT) 核心算法。

该模块只负责掌握度计算和状态持久化，不依赖 Neo4j 或 Web 框架。
`BKTEngine` 适合测试和单机使用；`AdaptiveBKTEngine` 使用 Redis 共享状态。
"""

from __future__ import annotations

import json
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from typing import Dict, Optional


@dataclass(frozen=True)
class BKTParameters:
    """BKT 四参数模型。"""

    p_init: float = 0.15
    p_learn: float = 0.25
    p_slip: float = 0.1
    p_guess: float = 0.2

    def __post_init__(self) -> None:
        for name, value in asdict(self).items():
            if not 0.0 <= value <= 1.0:
                raise ValueError(f"{name} 必须介于 0 和 1 之间")
        if self.p_slip + self.p_guess >= 1.0:
            raise ValueError("p_slip + p_guess 必须小于 1")


@dataclass
class MasteryState:
    """一名学生在某知识点上的掌握状态。"""

    student_id: str
    knowledge_id: str
    mastery_level: float
    attempts: int = 0
    correct_count: int = 0
    error_count: int = 0
    last_updated: str = ""
    learning_velocity: float = 0.0

    def __post_init__(self) -> None:
        self.mastery_level = _probability(self.mastery_level)
        if not self.last_updated:
            self.last_updated = _utc_now()


def _utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def _probability(value: float) -> float:
    return min(1.0, max(0.0, float(value)))


class BKTEngine:
    """内存版 BKT 引擎。"""

    def __init__(self, default_parameters: Optional[BKTParameters] = None):
        self.default_parameters = default_parameters or BKTParameters()
        self._states: Dict[str, MasteryState] = {}

    @staticmethod
    def _state_key(student_id: str, knowledge_id: str) -> str:
        if not student_id or not knowledge_id:
            raise ValueError("student_id 和 knowledge_id 不能为空")
        return f"{student_id}:{knowledge_id}"

    def _load_state(self, student_id: str, knowledge_id: str) -> Optional[MasteryState]:
        return self._states.get(self._state_key(student_id, knowledge_id))

    def _save_state(self, state: MasteryState) -> None:
        self._states[self._state_key(state.student_id, state.knowledge_id)] = state

    def get_mastery_state(
        self,
        student_id: str,
        knowledge_id: str,
        parameters: Optional[BKTParameters] = None,
    ) -> MasteryState:
        state = self._load_state(student_id, knowledge_id)
        if state is not None:
            return state
        params = parameters or self.default_parameters
        return MasteryState(student_id, knowledge_id, params.p_init)

    @staticmethod
    def calculate_next_mastery(
        current_mastery: float,
        is_correct: bool,
        parameters: BKTParameters,
    ) -> float:
        """先根据观测结果做贝叶斯更新，再应用一次学习转移。"""
        mastery = _probability(current_mastery)
        if is_correct:
            mastered_likelihood = 1.0 - parameters.p_slip
            unmastered_likelihood = parameters.p_guess
        else:
            mastered_likelihood = parameters.p_slip
            unmastered_likelihood = 1.0 - parameters.p_guess

        denominator = (
            mastery * mastered_likelihood
            + (1.0 - mastery) * unmastered_likelihood
        )
        posterior = (
            mastery * mastered_likelihood / denominator
            if denominator > 0.0
            else mastery
        )
        learned = posterior + (1.0 - posterior) * parameters.p_learn
        return _probability(learned)

    def update_mastery(
        self,
        student_id: str,
        knowledge_id: str,
        is_correct: bool,
        parameters: Optional[BKTParameters] = None,
    ) -> MasteryState:
        params = parameters or self.default_parameters
        previous = self.get_mastery_state(student_id, knowledge_id, params)
        new_level = self.calculate_next_mastery(
            previous.mastery_level, bool(is_correct), params
        )
        state = MasteryState(
            student_id=student_id,
            knowledge_id=knowledge_id,
            mastery_level=new_level,
            attempts=previous.attempts + 1,
            correct_count=previous.correct_count + int(bool(is_correct)),
            error_count=previous.error_count + int(not bool(is_correct)),
            last_updated=_utc_now(),
            learning_velocity=new_level - previous.mastery_level,
        )
        self._save_state(state)
        return state

    def predict_success_probability(
        self,
        student_id: str,
        knowledge_id: str,
        parameters: Optional[BKTParameters] = None,
    ) -> float:
        params = parameters or self.default_parameters
        mastery = self.get_mastery_state(
            student_id, knowledge_id, params
        ).mastery_level
        probability = (
            mastery * (1.0 - params.p_slip)
            + (1.0 - mastery) * params.p_guess
        )
        return _probability(probability)


class AdaptiveBKTEngine(BKTEngine):
    """Redis 持久化的 BKT 引擎，用于多进程/多实例部署。"""

    KEY_PREFIX = "codepath:bkt:mastery"

    def __init__(
        self,
        redis_client,
        default_parameters: Optional[BKTParameters] = None,
        state_ttl_seconds: Optional[int] = None,
    ):
        super().__init__(default_parameters)
        self.redis_client = redis_client
        self.state_ttl_seconds = state_ttl_seconds

    def _redis_key(self, student_id: str, knowledge_id: str) -> str:
        return f"{self.KEY_PREFIX}:{self._state_key(student_id, knowledge_id)}"

    def _load_state(self, student_id: str, knowledge_id: str) -> Optional[MasteryState]:
        raw = self.redis_client.get(self._redis_key(student_id, knowledge_id))
        if raw is None:
            return None
        if isinstance(raw, bytes):
            raw = raw.decode("utf-8")
        return MasteryState(**json.loads(raw))

    def _save_state(self, state: MasteryState) -> None:
        key = self._redis_key(state.student_id, state.knowledge_id)
        value = json.dumps(asdict(state), ensure_ascii=False)
        if self.state_ttl_seconds:
            self.redis_client.setex(key, self.state_ttl_seconds, value)
        else:
            self.redis_client.set(key, value)


if __name__ == "__main__":
    engine = BKTEngine()
    for result in (False, True, True):
        state = engine.update_mastery("demo-student", "python-list", result)
        print(
            f"result={result!s:<5} mastery={state.mastery_level:.3f} "
            f"success={engine.predict_success_probability('demo-student', 'python-list'):.3f}"
        )
