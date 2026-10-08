import logging
import threading
from contextlib import asynccontextmanager

from fastapi import FastAPI, Header, HTTPException

from app.bkt import BKTUpdateRequest, BKTUpdateResponse, update_bkt
from app.config import settings
from app.coach import CoachRequest, CoachResponse, coach
from app.learning_plan import GeneratedLearningPlan, LearningPlanRequest, generate_learning_plan
from app.practice import (
    GeneratedPractice,
    PracticeEvaluation,
    PracticeEvaluationRequest,
    PracticeGenerationRequest,
    evaluate_practice,
    generate_practice,
)
from app.worker import run_diagnosis_worker

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")


@asynccontextmanager
async def lifespan(_: FastAPI):
    worker = threading.Thread(target=run_diagnosis_worker, daemon=True, name="diagnosis-worker")
    worker.start()
    yield


app = FastAPI(
    title="CodePath AI Service",
    version="0.1.0",
    lifespan=lifespan,
)


@app.get("/health")
def health() -> dict:
    return {
        "status": "ok",
        "llmConfigured": settings.llm_configured,
        "diagnosisFallback": "rule-engine",
    }


@app.post("/v1/coach/chat", response_model=CoachResponse)
def coach_chat(request: CoachRequest) -> CoachResponse:
    try:
        return coach(request)
    except Exception as exc:
        logging.exception("coach request failed")
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.post("/v1/coach/hint", response_model=CoachResponse)
def coach_hint(request: CoachRequest) -> CoachResponse:
    if request.hintLevel is None:
        raise HTTPException(status_code=400, detail="hintLevel 必填")
    return coach_chat(request)


@app.post("/v1/mastery/update", response_model=BKTUpdateResponse)
def mastery_update(
    request: BKTUpdateRequest,
    x_internal_token: str = Header(default=""),
) -> BKTUpdateResponse:
    if x_internal_token != settings.internal_token:
        raise HTTPException(status_code=401, detail="invalid internal token")
    return update_bkt(request)


@app.post("/v1/practice/generate", response_model=GeneratedPractice)
def practice_generate(
    request: PracticeGenerationRequest,
    x_internal_token: str = Header(default=""),
) -> GeneratedPractice:
    if x_internal_token != settings.internal_token:
        raise HTTPException(status_code=401, detail="invalid internal token")
    try:
        return generate_practice(request)
    except Exception as exc:
        logging.exception("practice generation failed")
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.post("/v1/practice/evaluate", response_model=PracticeEvaluation)
def practice_evaluate(
    request: PracticeEvaluationRequest,
    x_internal_token: str = Header(default=""),
) -> PracticeEvaluation:
    if x_internal_token != settings.internal_token:
        raise HTTPException(status_code=401, detail="invalid internal token")
    try:
        return evaluate_practice(request)
    except Exception as exc:
        logging.exception("practice evaluation failed")
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.post("/v1/learning-plans/generate", response_model=GeneratedLearningPlan)
def learning_plan_generate(
    request: LearningPlanRequest,
    x_internal_token: str = Header(default=""),
) -> GeneratedLearningPlan:
    if x_internal_token != settings.internal_token:
        raise HTTPException(status_code=401, detail="invalid internal token")
    try:
        return generate_learning_plan(request)
    except Exception as exc:
        logging.exception("learning plan generation failed")
        raise HTTPException(status_code=502, detail=str(exc)) from exc
