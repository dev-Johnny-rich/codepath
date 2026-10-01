import base64
import time

import docker
from docker.errors import ContainerError, DockerException, ImageNotFound

from .config import settings
from .models import CaseResult, ExecutionResult, ExecutionTask, TestCase


class DockerExecutor:
    def __init__(self) -> None:
        self.client = docker.from_env()
        self._ready_images: set[str] = set()

    def execute(self, task: ExecutionTask) -> ExecutionResult:
        try:
            image, filename, run_command, compile_seconds = self._language(task.language)
            self._ensure_image(image)
        except ValueError as exc:
            return self._infrastructure_failure(task, str(exc))

        results: list[CaseResult] = []
        started = time.monotonic()
        for test_case in task.test_cases:
            results.append(self._run_case(task, test_case, image, filename, run_command, compile_seconds))

        passed = sum(item.status == "PASSED" for item in results)
        error_message = next((item.stderr for item in results if item.stderr), None)
        infrastructure_failure = any(item.status == "SANDBOX_ERROR" for item in results)
        return ExecutionResult(
            passedCount=passed,
            totalCount=len(results),
            runtimeMs=int((time.monotonic() - started) * 1000),
            memoryKb=None,
            errorMessage=error_message,
            infrastructureFailure=infrastructure_failure,
            results=results,
        )

    def _ensure_image(self, image: str) -> None:
        if image in self._ready_images:
            return
        try:
            self.client.images.get(image)
        except ImageNotFound:
            self.client.images.pull(image)
        self._ready_images.add(image)

    def _run_case(self, task: ExecutionTask, test_case: TestCase, image: str, filename: str,
                  run_command: str, compile_seconds: int) -> CaseResult:
        container = None
        started = time.monotonic()
        try:
            container = self.client.containers.create(
                image=image,
                command=["sh", "-c", f"while [ ! -f /workspace/.ready ]; do sleep 0.01; done; {run_command}"],
                working_dir="/workspace",
                network_disabled=True,
                read_only=True,
                mem_limit=f"{task.memory_limit_mb}m",
                memswap_limit=f"{task.memory_limit_mb}m",
                nano_cpus=500_000_000,
                pids_limit=32,
                cap_drop=["ALL"],
                security_opt=["no-new-privileges:true"],
                tmpfs={
                    "/tmp": "rw,noexec,nosuid,size=16m",
                    "/workspace": "rw,nosuid,size=64m",
                },
            )
            container.start()
            self._write_workspace(container, filename, task.source_code, test_case.input or "")
            try:
                wait_result = container.wait(timeout=max(1, task.time_limit_ms / 1000) + compile_seconds)
            except Exception:
                container.kill()
                return self._case_result(test_case.id, "TIME_LIMIT_EXCEEDED", "", "执行超时", started)

            stdout_bytes = container.logs(stdout=True, stderr=False)
            stderr_bytes = container.logs(stdout=False, stderr=True)
            stdout = self._decode(stdout_bytes)
            stderr = self._decode(stderr_bytes)
            if len(stdout.encode()) > settings.max_output_bytes:
                return self._case_result(test_case.id, "OUTPUT_LIMIT_EXCEEDED", "", "输出超过限制", started)

            exit_code = int(wait_result.get("StatusCode", 1))
            if exit_code != 0:
                return self._case_result(test_case.id, "RUNTIME_ERROR", stdout, stderr, started)

            actual = self._normalize(stdout)
            status = "PASSED" if test_case.expected_output is None else (
                "PASSED" if actual == self._normalize(test_case.expected_output) else "WRONG_ANSWER"
            )
            return self._case_result(test_case.id, status, stdout, stderr, started)
        except (DockerException, ContainerError) as exception:
            return self._case_result(test_case.id, "SANDBOX_ERROR", "", str(exception), started)
        finally:
            if container is not None:
                try:
                    container.remove(force=True)
                except DockerException:
                    pass

    @staticmethod
    def _write_workspace(container, filename: str, source_code: str, input_data: str) -> None:
        source_payload = base64.b64encode(source_code.encode("utf-8")).decode("ascii")
        input_payload = base64.b64encode(input_data.encode("utf-8")).decode("ascii")
        command = (
            f"printf %s '{source_payload}' | base64 -d > /workspace/{filename}; "
            f"printf %s '{input_payload}' | base64 -d > /workspace/input.txt; "
            f"chmod 0444 /workspace/{filename} /workspace/input.txt; touch /workspace/.ready"
        )
        result = container.exec_run(["sh", "-c", command])
        if result.exit_code != 0:
            raise DockerException(f"无法写入沙箱工作区：{DockerExecutor._decode(result.output)}")

    @staticmethod
    def _language(language: str) -> tuple[str, str, str, int]:
        value = language.lower()
        if value in {"python", "python3"}:
            return settings.python_image, "main.py", "python -I -B /workspace/main.py < /workspace/input.txt", 1
        if value == "java":
            return settings.java_image, "Main.java", "javac /workspace/Main.java -d /workspace && java -cp /workspace Main < /workspace/input.txt", 10
        if value in {"cpp", "c++"}:
            return settings.cpp_image, "main.cpp", "g++ -O2 -std=c++17 /workspace/main.cpp -o /workspace/main && /workspace/main < /workspace/input.txt", 15
        raise ValueError(f"不支持的语言: {language}")

    @staticmethod
    def _decode(value: bytes | None) -> str:
        return (value or b"").decode("utf-8", errors="replace")

    @staticmethod
    def _normalize(value: str) -> str:
        return "\n".join(line.rstrip() for line in value.strip().splitlines())

    @staticmethod
    def _case_result(test_case_id: int, status: str, stdout: str, stderr: str, started: float) -> CaseResult:
        return CaseResult(
            testCaseId=test_case_id,
            status=status,
            actualOutput=stdout,
            stderr=stderr,
            runtimeMs=int((time.monotonic() - started) * 1000),
            memoryKb=None,
        )

    @staticmethod
    def _infrastructure_failure(task: ExecutionTask, message: str) -> ExecutionResult:
        return ExecutionResult(
            passedCount=0,
            totalCount=len(task.test_cases),
            runtimeMs=0,
            memoryKb=None,
            errorMessage=message,
            infrastructureFailure=True,
            results=[],
        )
