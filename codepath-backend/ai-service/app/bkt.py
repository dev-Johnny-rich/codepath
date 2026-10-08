"""Stateless BKT calculation used by the Spring business service."""

from pydantic import BaseModel, Field


class BKTUpdateRequest(BaseModel):
    current_mastery: float | None = Field(default=None, alias="currentMastery", ge=0, le=1)
    evidence_count: int = Field(default=0, alias="evidenceCount", ge=0)
    pass_rate: float = Field(alias="passRate", ge=0, le=1)
    knowledge_weight: float = Field(default=1.0, alias="knowledgeWeight", gt=0, le=1)

    model_config = {"populate_by_name": True}


class BKTUpdateResponse(BaseModel):
    mastery_before: float = Field(alias="masteryBefore")
    mastery_after: float = Field(alias="masteryAfter")
    delta: float
    predicted_success: float = Field(alias="predictedSuccess")
    model_name: str = Field(default="bkt-v1", alias="modelName")

    model_config = {"populate_by_name": True}


def update_bkt(request: BKTUpdateRequest) -> BKTUpdateResponse:
    """Update mastery with soft evidence derived from the test pass rate.

    A pass rate of 1 is a correct BKT observation and 0 is an incorrect one.
    Partial pass rates interpolate both likelihoods, so one submission contributes
    one bounded observation regardless of how many test cases the problem has.
    """

    p_init = 0.15
    p_learn = 0.25 * request.knowledge_weight
    p_slip = 0.10
    p_guess = 0.20
    before = request.current_mastery if request.current_mastery is not None else p_init

    mastered_likelihood = (
        request.pass_rate * (1.0 - p_slip)
        + (1.0 - request.pass_rate) * p_slip
    )
    unmastered_likelihood = (
        request.pass_rate * p_guess
        + (1.0 - request.pass_rate) * (1.0 - p_guess)
    )
    denominator = before * mastered_likelihood + (1.0 - before) * unmastered_likelihood
    posterior = before if denominator == 0 else before * mastered_likelihood / denominator
    after = posterior + (1.0 - posterior) * p_learn
    after = min(1.0, max(0.0, after))
    predicted = after * (1.0 - p_slip) + (1.0 - after) * p_guess

    return BKTUpdateResponse(
        masteryBefore=round(before, 5),
        masteryAfter=round(after, 5),
        delta=round(after - before, 5),
        predictedSuccess=round(predicted, 5),
    )
