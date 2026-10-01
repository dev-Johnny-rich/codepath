import json
import logging
import time

import httpx
import redis

from app.config import settings
from app.diagnosis.rule_analyzer import diagnose_with_rules
from app.llm.client import DeepSeekDiagnosisModel
from app.models import DiagnosisTask

logger = logging.getLogger("codepath-ai-worker")


def _headers() -> dict[str, str]:
    return {"X-Internal-Token": settings.internal_token}


def run_diagnosis_worker() -> None:
    queue = redis.Redis(
        host=settings.redis_host,
        port=settings.redis_port,
        password=settings.redis_password,
        decode_responses=True,
    )
    with httpx.Client(timeout=30.0) as client:
        logger.info("diagnosis worker started; queue=%s", settings.diagnosis_queue)
        while True:
            submission_id = None
            payload = {}
            try:
                item = queue.blpop(settings.diagnosis_queue, timeout=10)
                if item is None:
                    continue
                payload = json.loads(item[1])
                submission_id = int(payload["submissionId"])
                response = client.get(
                    f"{settings.business_api_url}/api/v1/internal/diagnoses/{submission_id}/task",
                    headers=_headers(),
                )
                response.raise_for_status()
                task = DiagnosisTask.model_validate(response.json()["data"])

                rule_result = diagnose_with_rules(task)
                result = rule_result
                if settings.llm_configured and rule_result.error_type != "NONE":
                    try:
                        result = DeepSeekDiagnosisModel().diagnose(
                            task, rule_result, client=client
                        )
                    except Exception:
                        logger.exception(
                            "DeepSeek diagnosis failed for submission %s; using rule fallback",
                            submission_id,
                        )
                callback = client.post(
                    f"{settings.business_api_url}/api/v1/internal/diagnoses/{submission_id}/result",
                    headers=_headers(),
                    json=result.model_dump(by_alias=True),
                )
                callback.raise_for_status()
                logger.info(
                    "submission %s diagnosed as %s by %s",
                    submission_id,
                    result.error_type,
                    result.model_name,
                )
            except Exception as exc:
                logger.exception("diagnosis worker loop failed")
                attempts = int(payload.get("attempts", 0)) + 1
                if submission_id is not None and attempts < settings.max_attempts:
                    queue.rpush(settings.diagnosis_queue, json.dumps({"submissionId": submission_id, "attempts": attempts}))
                elif submission_id is not None:
                    queue.rpush(settings.diagnosis_dlq, json.dumps({
                        "submissionId": submission_id, "attempts": attempts, "error": str(exc)
                    }))
                time.sleep(2)
