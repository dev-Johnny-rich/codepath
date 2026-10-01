from pydantic import BaseModel, Field


class TestCase(BaseModel):
    id: int
    input: str | None = ""
    expected_output: str | None = Field(default=None, alias="expectedOutput")


class ExecutionTask(BaseModel):
    submission_id: int = Field(alias="submissionId")
    language: str
    source_code: str = Field(alias="sourceCode")
    time_limit_ms: int = Field(alias="timeLimitMs")
    memory_limit_mb: int = Field(alias="memoryLimitMb")
    test_cases: list[TestCase] = Field(alias="testCases")


class CaseResult(BaseModel):
    test_case_id: int = Field(alias="testCaseId")
    status: str
    actual_output: str = Field(alias="actualOutput")
    stderr: str
    runtime_ms: int = Field(alias="runtimeMs")
    memory_kb: int | None = Field(default=None, alias="memoryKb")

    model_config = {"populate_by_name": True}


class ExecutionResult(BaseModel):
    passed_count: int = Field(alias="passedCount")
    total_count: int = Field(alias="totalCount")
    runtime_ms: int = Field(alias="runtimeMs")
    memory_kb: int | None = Field(default=None, alias="memoryKb")
    error_message: str | None = Field(default=None, alias="errorMessage")
    infrastructure_failure: bool = Field(default=False, alias="infrastructureFailure")
    results: list[CaseResult]

    model_config = {"populate_by_name": True}
