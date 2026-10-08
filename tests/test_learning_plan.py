import sys
from pathlib import Path


AI_SERVICE_ROOT = Path(__file__).resolve().parents[1] / "codepath-backend" / "ai-service"
sys.path.insert(0, str(AI_SERVICE_ROOT))

from app import learning_plan  # noqa: E402


def test_learning_plan_parses_structured_ai_result(monkeypatch):
    monkeypatch.setattr(learning_plan.settings, "llm_model", "synthetic-model")
    monkeypatch.setattr(
        learning_plan,
        "_chat",
        lambda *_args, **_kwargs: {
            "title": "Python 基础强化计划",
            "summary": "先补齐循环，再练习列表。",
            "stages": [
                {
                    "weekNumber": 1,
                    "title": "循环基础",
                    "objective": "能使用 for 循环处理输入。",
                    "items": [
                        {
                            "taskType": "PRACTICE",
                            "title": "统计偶数",
                            "description": "完成现有练习题。",
                            "knowledgePointId": 3,
                            "problemId": 4,
                            "estimatedMinutes": 30,
                            "targetMastery": 0.8,
                        }
                    ],
                }
            ],
        },
    )
    request = learning_plan.LearningPlanRequest(
        courseName="Python 程序设计基础",
        language="python",
        goal="掌握 Python 基础",
        currentLevel="BEGINNER",
        daysPerWeek=5,
        minutesPerDay=45,
        durationWeeks=4,
        knowledgePoints=[learning_plan.PlanKnowledge(id=3, name="循环结构", mastery=0.35)],
        problems=[learning_plan.PlanProblem(id=4, title="统计偶数", knowledgePointIds=[3])],
    )

    result = learning_plan.generate_learning_plan(request)

    assert result.title == "Python 基础强化计划"
    assert result.stages[0].items[0].problemId == 4
    assert result.stages[0].items[0].targetMastery == 0.8
    assert result.modelName == "synthetic-model"


def test_learning_plan_repairs_current_mastery_used_as_target(monkeypatch):
    monkeypatch.setattr(learning_plan.settings, "llm_model", "synthetic-model")
    monkeypatch.setattr(
        learning_plan,
        "_chat",
        lambda *_args, **_kwargs: {
            "title": "Java 学习计划",
            "summary": "循序渐进。",
            "stages": [{
                "weekNumber": 1,
                "title": "语法基础",
                "objective": "掌握基本语法。",
                "items": [{
                    "taskType": "PRACTICE",
                    "title": "基础练习",
                    "description": "完成练习。",
                    "knowledgePointId": 101,
                    "problemId": None,
                    "estimatedMinutes": 30,
                    "targetMastery": 0.3,
                }],
            }],
        },
    )
    request = learning_plan.LearningPlanRequest(
        courseName="Java 编程学习路径",
        language="java",
        goal="掌握 Java 基础",
        currentLevel="BEGINNER",
        daysPerWeek=5,
        minutesPerDay=45,
        durationWeeks=4,
        knowledgePoints=[learning_plan.PlanKnowledge(id=101, name="Java 语法", mastery=0.3)],
        problems=[],
    )

    result = learning_plan.generate_learning_plan(request)

    assert result.stages[0].items[0].targetMastery == 0.8
