import ast
import re

from app.models import DiagnosisResult, DiagnosisTask, Evidence


ERROR_PATTERNS = (
    ("IndentationError", "INDENTATION_ERROR", "代码缩进不符合 Python 语法要求"),
    ("SyntaxError", "SYNTAX_ERROR", "代码包含语法错误，解释器无法开始执行"),
    ("NameError", "UNDEFINED_VARIABLE", "代码使用了尚未定义的变量或函数"),
    ("TypeError", "TYPE_MISMATCH", "运算或函数调用使用了不兼容的数据类型"),
    ("IndexError", "INDEX_OUT_OF_RANGE", "代码访问了列表有效范围之外的下标"),
    ("ZeroDivisionError", "DIVISION_BY_ZERO", "代码执行了除数为零的运算"),
    ("RecursionError", "RECURSION_TERMINATION", "递归缺少有效终止条件或递归深度过大"),
    ("TIME_LIMIT_EXCEEDED", "TIME_COMPLEXITY", "程序超过题目规定的执行时间"),
)


def diagnose_with_rules(task: DiagnosisTask) -> DiagnosisResult:
    if task.total_count > 0 and task.passed_count == task.total_count:
        return DiagnosisResult(
            errorType="NONE",
            rootCause="所有测试用例均已通过",
            explanation="本次提交通过了当前题目的全部测试用例，可以继续挑战下一道题。",
            confidence=1.0,
            hintLevel=1,
            nextAction="NEXT_PROBLEM",
            modelName="rule-engine",
            promptVersion="rules-v1",
            evidence=[Evidence(evidenceType="TEST_CASE", content="全部测试用例通过")],
        )

    syntax_evidence = _syntax_evidence(task.source_code)
    combined_error = "\n".join(
        value for value in [task.error_message, *(item.stderr for item in task.test_results)] if value
    )
    error_type, root_cause = _classify(combined_error, task.test_results)
    evidence = syntax_evidence + _runtime_evidence(task)
    knowledge_names = "、".join(item.name for item in task.knowledge_points)
    explanation = root_cause
    if knowledge_names:
        explanation += f"。建议回顾相关知识点：{knowledge_names}。"

    return DiagnosisResult(
        errorType=error_type,
        rootCause=root_cause,
        explanation=explanation,
        confidence=0.72 if combined_error else 0.55,
        hintLevel=1,
        nextAction="REVIEW_AND_RETRY",
        modelName="rule-engine",
        promptVersion="rules-v1",
        evidence=evidence or [Evidence(evidenceType="TEST_CASE", content="存在未通过的测试用例")],
    )


def _syntax_evidence(source_code: str) -> list[Evidence]:
    try:
        ast.parse(source_code)
        return []
    except SyntaxError as exception:
        return [Evidence(
            evidenceType="AST",
            lineNumber=exception.lineno,
            content=exception.msg,
            sourceReference=f"line:{exception.lineno}" if exception.lineno else None,
        )]


def _classify(error_text: str, test_results: list) -> tuple[str, str]:
    for marker, error_type, explanation in ERROR_PATTERNS:
        if marker in error_text or any(item.status == marker for item in test_results):
            return error_type, explanation
    if any(item.status == "WRONG_ANSWER" for item in test_results):
        return "LOGIC_OR_BOUNDARY", "程序能够运行，但输出与预期不一致，可能遗漏了边界条件或核心逻辑"
    if re.search(r"killed|memory", error_text, re.IGNORECASE):
        return "MEMORY_LIMIT", "程序使用的内存超过限制"
    return "UNKNOWN", "当前证据不足以确定唯一根因，需要结合代码结构进一步分析"


def _runtime_evidence(task: DiagnosisTask) -> list[Evidence]:
    evidence = []
    for item in task.test_results:
        if item.status != "PASSED":
            message = item.stderr or f"测试状态：{item.status}"
            evidence.append(Evidence(
                evidenceType="TEST_CASE",
                content=message[:2000],
                sourceReference=f"test-case:{item.test_case_id}",
            ))
    return evidence[:8]
