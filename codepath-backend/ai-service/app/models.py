from pydantic import BaseModel, Field


class KnowledgePoint(BaseModel):
    id: int
    code: str
    name: str
    description: str | None = None


class TestEvidence(BaseModel):
    test_case_id: int = Field(alias="testCaseId")
    status: str
    actual_output: str | None = Field(default=None, alias="actualOutput")
    stderr: str | None = None


class DiagnosisTask(BaseModel):
    submission_id: int = Field(alias="submissionId")
    user_id: int = Field(alias="userId")
    problem_id: int = Field(alias="problemId")
    problem_title: str = Field(alias="problemTitle")
    problem_description: str = Field(alias="problemDescription")
    language: str
    source_code: str = Field(alias="sourceCode")
    error_message: str | None = Field(default=None, alias="errorMessage")
    passed_count: int = Field(alias="passedCount")
    total_count: int = Field(alias="totalCount")
    test_results: list[TestEvidence] = Field(alias="testResults")
    knowledge_points: list[KnowledgePoint] = Field(alias="knowledgePoints")


class Evidence(BaseModel):
    evidence_type: str = Field(alias="evidenceType")
    line_number: int | None = Field(default=None, alias="lineNumber")
    content: str
    source_reference: str | None = Field(default=None, alias="sourceReference")

    model_config = {"populate_by_name": True}


class DiagnosisResult(BaseModel):
    error_type: str = Field(alias="errorType")
    root_cause: str = Field(alias="rootCause")
    explanation: str
    confidence: float = Field(ge=0, le=1)
    hint_level: int = Field(alias="hintLevel", ge=1, le=5)
    next_action: str = Field(alias="nextAction")
    model_name: str = Field(alias="modelName")
    prompt_version: str = Field(alias="promptVersion")
    evidence: list[Evidence]

    model_config = {"populate_by_name": True}
