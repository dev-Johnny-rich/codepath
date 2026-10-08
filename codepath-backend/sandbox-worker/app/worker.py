import json
import logging
import time

import httpx
import redis

from .config import settings
from .executor import DockerExecutor
from .models import ExecutionTask

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger("codepath-sandbox")


def _headers() -> dict[str, str]:
    return {"X-Internal-Token": settings.internal_token}


def _get_task(client: httpx.Client, submission_id: int) -> ExecutionTask:
    response = client.get(
        f"{settings.business_api_url}/api/v1/internal/submissions/{submission_id}/execution-task",
        headers=_headers(),
    )
    response.raise_for_status()
    return ExecutionTask.model_validate(response.json()["data"])


def _set_stage(client: httpx.Client, submission_id: int, stage: str) -> None:
    response = client.post(
        f"{settings.business_api_url}/api/v1/internal/submissions/{submission_id}/stage",
        headers=_headers(),
        json={"status": stage},
    )
    response.raise_for_status()


def run() -> None:
    queue = redis.Redis(
        host=settings.redis_host,
        port=settings.redis_port,
        password=settings.redis_password,
        decode_responses=True,
    )
    executor = DockerExecutor()
    with httpx.Client(timeout=15.0) as client:
        logger.info("sandbox worker started; queue=%s", settings.queue_name)
        executor.preload_images(lambda state, image: logger.info("sandbox image %s: %s", state, image))
        while True:
            submission_id = None
            task = None
            try:
                item = queue.blpop(settings.queue_name, timeout=10)
                if item is None:
                    continue
                payload = json.loads(item[1])
                attempts = int(payload.get("attempts", 0))
                submission_id = int(payload["submissionId"])
                task = _get_task(client, submission_id)
                if attempts == 0:
                    client.post(
                        f"{settings.business_api_url}/api/v1/internal/submissions/{submission_id}/started",
                        headers=_headers(),
                    ).raise_for_status()
                result = executor.execute(task, lambda stage: _set_stage(client, submission_id, stage))
                client.post(
                    f"{settings.business_api_url}/api/v1/internal/submissions/{submission_id}/execution-result",
                    headers=_headers(),
                    json=result.model_dump(by_alias=True),
                ).raise_for_status()
                logger.info("submission %s executed", submission_id)
            except Exception as exc:
                logger.exception("worker loop failed")
                attempts = int(payload.get("attempts", 0)) + 1 if 'payload' in locals() else 1
                if submission_id is not None and attempts < settings.max_attempts:
                    queue.rpush(settings.queue_name, json.dumps({"submissionId": submission_id, "attempts": attempts}))
                    logger.warning("submission %s requeued, attempt=%s", submission_id, attempts)
                elif submission_id is not None and task is not None:
                    queue.rpush(settings.dead_letter_queue, json.dumps({
                        "submissionId": submission_id, "attempts": attempts, "error": str(exc)
                    }))
                    try:
                        client.post(
                            f"{settings.business_api_url}/api/v1/internal/submissions/{submission_id}/execution-result",
                            headers=_headers(),
                            json={
                                "passedCount": 0,
                                "totalCount": len(task.test_cases),
                                "runtimeMs": 0,
                                "memoryKb": None,
                                "errorMessage": "沙箱 Worker 执行失败，请稍后重试",
                                "infrastructureFailure": True,
                                "results": [],
                            },
                        ).raise_for_status()
                    except Exception:
                        logger.exception("failed to report sandbox infrastructure failure")
                time.sleep(2)


if __name__ == "__main__":
    run()
