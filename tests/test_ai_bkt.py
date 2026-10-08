import importlib.util
from pathlib import Path


MODULE_PATH = (
    Path(__file__).resolve().parents[1]
    / "codepath-backend"
    / "ai-service"
    / "app"
    / "bkt.py"
)
SPEC = importlib.util.spec_from_file_location("codepath_ai_bkt", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
SPEC.loader.exec_module(MODULE)


def calculate(pass_rate: float) -> float:
    request = MODULE.BKTUpdateRequest(
        currentMastery=0.4,
        evidenceCount=2,
        passRate=pass_rate,
        knowledgeWeight=1.0,
    )
    return MODULE.update_bkt(request).mastery_after


def test_test_pass_rate_produces_ordered_mastery_updates():
    failed = calculate(0.0)
    partial = calculate(0.5)
    passed = calculate(1.0)

    assert 0.0 <= failed < partial < passed <= 1.0


def test_secondary_knowledge_point_has_smaller_learning_transfer():
    primary = MODULE.update_bkt(MODULE.BKTUpdateRequest(
        currentMastery=0.4, evidenceCount=2, passRate=1.0, knowledgeWeight=1.0
    ))
    secondary = MODULE.update_bkt(MODULE.BKTUpdateRequest(
        currentMastery=0.4, evidenceCount=2, passRate=1.0, knowledgeWeight=0.4
    ))

    assert secondary.mastery_after < primary.mastery_after
