from dataclasses import asdict

import pytest

from bkt_algorithm import AdaptiveBKTEngine, BKTEngine, BKTParameters


class FakeRedis:
    def __init__(self):
        self.values = {}

    def get(self, key):
        return self.values.get(key)

    def set(self, key, value):
        self.values[key] = value

    def setex(self, key, _ttl, value):
        self.values[key] = value


def test_correct_answer_increases_mastery_and_counters():
    engine = BKTEngine(BKTParameters(p_init=0.2))

    state = engine.update_mastery("student-1", "lists", True)

    assert state.mastery_level > 0.2
    assert state.attempts == 1
    assert state.correct_count == 1
    assert state.error_count == 0
    assert 0.0 <= engine.predict_success_probability("student-1", "lists") <= 1.0


def test_incorrect_answer_records_error():
    engine = BKTEngine(BKTParameters(p_init=0.5))

    state = engine.update_mastery("student-1", "loops", False)

    assert state.attempts == 1
    assert state.correct_count == 0
    assert state.error_count == 1


def test_redis_engine_persists_state_across_instances():
    redis = FakeRedis()
    first = AdaptiveBKTEngine(redis)
    expected = first.update_mastery("student-2", "functions", True)

    second = AdaptiveBKTEngine(redis)
    actual = second.get_mastery_state("student-2", "functions")

    assert asdict(actual) == asdict(expected)


@pytest.mark.parametrize(
    "kwargs",
    [
        {"p_init": -0.1},
        {"p_learn": 1.1},
        {"p_slip": 0.6, "p_guess": 0.4},
    ],
)
def test_invalid_parameters_are_rejected(kwargs):
    with pytest.raises(ValueError):
        BKTParameters(**kwargs)
