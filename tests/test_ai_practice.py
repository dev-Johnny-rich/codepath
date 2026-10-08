import sys
from pathlib import Path


AI_SERVICE_ROOT = Path(__file__).resolve().parents[1] / "codepath-backend" / "ai-service"
sys.path.insert(0, str(AI_SERVICE_ROOT))

from app import practice  # noqa: E402


class FakeResponse:
    def __init__(self, content: str):
        self.content = content

    def raise_for_status(self):
        return None

    def json(self):
        return {"choices": [{"message": {"content": self.content}}]}


def configure_fake_llm(monkeypatch, content: str):
    monkeypatch.setattr(practice.settings, "llm_base_url", "https://example.invalid/v1")
    monkeypatch.setattr(practice.settings, "llm_api_key", "synthetic-test-key")
    monkeypatch.setattr(practice.settings, "llm_model", "synthetic-model")
    monkeypatch.setattr(
        practice.httpx,
        "post",
        lambda *args, **kwargs: FakeResponse(content),
    )


def test_generate_practice_parses_structured_model_result(monkeypatch):
    configure_fake_llm(
        monkeypatch,
        """```json
        {
          "title": "循环计数专项",
          "description": "统计输入中的偶数个数。",
          "inputDescription": "一行整数。",
          "outputDescription": "输出偶数数量。",
          "starterCode": "nums = list(map(int, input().split()))",
          "stdin": "1 2 4 7",
          "evaluationCriteria": "输出应为 2。"
        }
        ```""",
    )
    request = practice.PracticeGenerationRequest(
        language="python",
        sourceTitle="列表求和",
        sourceDiagnosis="循环边界掌握不足",
        knowledgePoints=[
            practice.PracticeKnowledge(
                id=7,
                code="LOOP",
                name="循环",
                description="循环与计数",
                mastery=0.36,
            )
        ],
    )

    generated = practice.generate_practice(request)

    assert generated.title == "循环计数专项"
    assert generated.stdin == "1 2 4 7"
    assert generated.evaluationCriteria == "输出应为 2。"
    assert generated.modelName == "synthetic-model"


def test_evaluation_does_not_treat_string_false_as_correct(monkeypatch):
    configure_fake_llm(
        monkeypatch,
        """{
          "correct": "false",
          "score": 0.95,
          "feedback": "输出了总和，不是偶数个数。",
          "errorType": "LOGIC_OR_BOUNDARY",
          "nextStep": "先判断每个数是否为偶数。"
        }""",
    )
    request = practice.PracticeEvaluationRequest(
        title="循环计数专项",
        description="统计输入中的偶数个数。",
        inputDescription="一行整数。",
        outputDescription="输出偶数数量。",
        evaluationCriteria="输入 1 2 4 7 时输出 2。",
        knowledgeSummary="循环",
        language="python",
        sourceCode="print(sum(map(int, input().split())))",
        stdin="1 2 4 7",
        stdout="14\n",
        stderr="",
    )

    result = practice.evaluate_practice(request)

    assert result.correct is False
    assert result.score == 0.95
    assert result.errorType == "LOGIC_OR_BOUNDARY"
