import logging
import threading
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException

from app.config import settings
from app.coach import CoachRequest, CoachResponse, coach
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
