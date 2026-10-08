import base64
import math
import shlex
import time
from collections.abc import Callable

import docker
from docker.errors import DockerException, ImageNotFound

from .config import settings
from .models import CaseResult, ExecutionResult, ExecutionTask, TestCase


class DockerExecutor:
    def __init__(self) -> None:
        self.client = docker.from_env(timeout=settings.docker_timeout_seconds)
        self._ready_images: set[str] = set()

    @property
    def images(self) -> tuple[str, ...]:
        return (settings.python_image, settings.java_image, settings.cpp_image, settings.go_image)

    def preload_images(self, on_progress: Callable[[str, str], None] | None = None) -> None:
        for image in self.images:
            if on_progress:
                on_progress("start", image)
            try:
                self._ensure_image(image)
                if on_progress:
                    on_progress("ready", image)
            except DockerException:
                if on_progress:
                    on_progress("error", image)

    def execute(self, task: ExecutionTask,
                on_stage: Callable[[str], None] | None = None) -> ExecutionResult:
        try:
            image, filename, compile_command, run_command, compile_seconds = self._language(task.language)
            self._ensure_image(image)
            if on_stage:
                on_stage("COMPILING")
            return self._execute_in_container(
                task, image, filename, compile_command, run_command, compile_seconds, on_stage
            )
        except (ValueError, DockerException) as exc:
            return self._infrastructure_failure(task, str(exc))

    def _ensure_image(self, image: str) -> None:
        if image in self._ready_images:
            return
        try:
            self.client.images.get(image)
        except ImageNotFound:
            self.client.images.pull(image)
        self._ready_images.add(image)

    def _execute_in_container(self, task: ExecutionTask, image: str, filename: str,
                              compile_command: str, run_command: str, compile_seconds: int,
                              on_stage: Callable[[str], None] | None) -> ExecutionResult:
        container = None
        task_started = time.monotonic()
        try:
            compile_script = (
                "while [ ! -f /workspace/.ready ]; do sleep 0.05; done; "
                f"timeout -s KILL {compile_seconds}s sh -c {shlex.quote(compile_command)} "
                "> /workspace/compile.stdout 2> /workspace/compile.stderr; "
                "echo $? > /workspace/compile.exit; "
                "find /workspace -type f -exec chmod a-w {} + 2>/dev/null || true; "
                "while [ ! -f /workspace/.done ]; do sleep 0.05; done"
            )
            compile_memory_mb = max(task.memory_limit_mb, 512)
            container = self.client.containers.create(
                image=image,
                command=["sh", "-c", compile_script],
                working_dir="/workspace",
                user="65534:65534",
                environment={"HOME": "/tmp", "GOCACHE": "/tmp/go-build", "GOMAXPROCS": "1", "GOFLAGS": "-p=1"},
                network_disabled=True,
                read_only=True,
                mem_limit=f"{compile_memory_mb}m",
                memswap_limit=f"{compile_memory_mb}m",
                nano_cpus=1_000_000_000,
                pids_limit=64,
                cap_drop=["ALL"],
                security_opt=["no-new-privileges:true"],
                tmpfs={
                    "/tmp": "rw,noexec,nosuid,size=128m,mode=1777",
                    "/workspace": "rw,exec,nosuid,size=128m,mode=1777",
                },
            )
            container.start()
            self._write_source(container, filename, task.source_code)
            compile_exit = self._wait_for_compile(container, compile_seconds + 3)
            compile_stdout = self._read_file(container, "/workspace/compile.stdout")
            compile_stderr = self._read_file(container, "/workspace/compile.stderr")
            if compile_exit != 0:
                status = "TIME_LIMIT_EXCEEDED" if compile_exit in {124, 137} else "COMPILE_ERROR"
                message = compile_stderr or compile_stdout or "编译失败"
                results = [self._case_result(case.id, status, "", message, task_started)
                           for case in task.test_cases]
                return self._result(results, task_started)

            container.update(
                mem_limit=f"{task.memory_limit_mb}m",
                memswap_limit=f"{task.memory_limit_mb}m",
            )
            if on_stage:
                on_stage("RUNNING")

            results: list[CaseResult] = []
            for test_case in task.test_cases:
                if time.monotonic() - task_started > settings.max_task_seconds:
                    results.append(self._case_result(
                        test_case.id, "TIME_LIMIT_EXCEEDED", "", "任务总执行时间超过限制", task_started
                    ))
                    continue
                results.append(self._run_case(container, task, test_case, run_command))
            return self._result(results, task_started)
        except DockerException as exc:
            return self._infrastructure_failure(task, str(exc))
        finally:
            if container is not None:
                try:
                    container.exec_run(["sh", "-c", "touch /workspace/.done"], user="0")
                    container.remove(force=True)
                except DockerException:
                    pass

    def _run_case(self, container, task: ExecutionTask, test_case: TestCase,
                  run_command: str) -> CaseResult:
        started = time.monotonic()
        input_path = f"/workspace/input-{test_case.id}.txt"
        try:
            self._write_input(container, input_path, test_case.input or "")
            run_seconds = max(1, math.ceil(task.time_limit_ms / 1000))
            command = f"timeout -s KILL {run_seconds}s sh -c {shlex.quote(run_command)} < {shlex.quote(input_path)}"
            execution = container.exec_run(["sh", "-c", command], user="65533:65533", demux=True)
            stdout_bytes, stderr_bytes = execution.output or (b"", b"")
            stdout = self._decode(stdout_bytes)
            stderr = self._decode(stderr_bytes)
            if len(stdout.encode()) > settings.max_output_bytes:
                return self._case_result(test_case.id, "OUTPUT_LIMIT_EXCEEDED", "", "输出超过限制", started)
            if execution.exit_code in {124, 137}:
                return self._case_result(test_case.id, "TIME_LIMIT_EXCEEDED", stdout, "执行超时", started)
            if execution.exit_code != 0:
                return self._case_result(test_case.id, "RUNTIME_ERROR", stdout, stderr, started)
            actual = self._normalize(stdout)
            status = "PASSED" if test_case.expected_output is None else (
                "PASSED" if actual == self._normalize(test_case.expected_output) else "WRONG_ANSWER"
            )
            return self._case_result(test_case.id, status, stdout, stderr, started)
        except DockerException as exc:
            return self._case_result(test_case.id, "SANDBOX_ERROR", "", str(exc), started)
        finally:
            try:
                container.exec_run(["rm", "-f", input_path], user="0")
            except DockerException:
                pass

    @staticmethod
    def _write_source(container, filename: str, source_code: str) -> None:
        payload = base64.b64encode(source_code.encode("utf-8")).decode("ascii")
        path = f"/workspace/{filename}"
        command = (
            f"printf %s {shlex.quote(payload)} | base64 -d > {shlex.quote(path)}; "
            f"chmod 0444 {shlex.quote(path)}; touch /workspace/.ready"
        )
        result = container.exec_run(["sh", "-c", command], user="0")
        if result.exit_code != 0:
            raise DockerException(f"无法写入沙箱源码：{DockerExecutor._decode(result.output)}")

    @staticmethod
    def _write_input(container, path: str, input_data: str) -> None:
        payload = base64.b64encode(input_data.encode("utf-8")).decode("ascii")
        command = f"printf %s {shlex.quote(payload)} | base64 -d > {shlex.quote(path)}; chmod 0444 {shlex.quote(path)}"
        result = container.exec_run(["sh", "-c", command], user="0")
        if result.exit_code != 0:
            raise DockerException(f"无法写入沙箱输入：{DockerExecutor._decode(result.output)}")

    @staticmethod
    def _wait_for_compile(container, timeout_seconds: int) -> int:
        deadline = time.monotonic() + timeout_seconds
        while time.monotonic() < deadline:
            result = container.exec_run(["sh", "-c", "cat /workspace/compile.exit 2>/dev/null"], user="0")
            if result.exit_code == 0:
                try:
                    return int(DockerExecutor._decode(result.output).strip())
                except ValueError:
                    return 1
            time.sleep(0.1)
        container.kill()
        return 124

    @staticmethod
    def _read_file(container, path: str) -> str:
        result = container.exec_run(["sh", "-c", f"cat {shlex.quote(path)} 2>/dev/null"], user="0")
        return DockerExecutor._decode(result.output)

    @staticmethod
    def _language(language: str) -> tuple[str, str, str, str, int]:
        value = language.lower()
        if value in {"python", "python3"}:
            return settings.python_image, "main.py", "true", "python -I -B /workspace/main.py", 2
        if value == "java":
            return settings.java_image, "Main.java", "javac /workspace/Main.java -d /workspace", "java -cp /workspace Main", 20
        if value in {"cpp", "c++"}:
            return settings.cpp_image, "main.cpp", "g++ -O2 -std=c++17 /workspace/main.cpp -o /workspace/main", "/workspace/main", 20
        if value in {"go", "golang"}:
            return settings.go_image, "main.go", "go build -trimpath -o /workspace/main /workspace/main.go", "/workspace/main", 60
        raise ValueError(f"不支持的语言: {language}")

    @staticmethod
    def _result(results: list[CaseResult], started: float) -> ExecutionResult:
        passed = sum(item.status == "PASSED" for item in results)
        error_message = next((item.stderr for item in results if item.stderr), None)
        return ExecutionResult(
            passedCount=passed,
            totalCount=len(results),
            runtimeMs=int((time.monotonic() - started) * 1000),
            memoryKb=None,
            errorMessage=error_message,
            infrastructureFailure=any(item.status == "SANDBOX_ERROR" for item in results),
            results=results,
        )

    @staticmethod
    def _decode(value: bytes | None) -> str:
        return (value or b"").decode("utf-8", errors="replace")

    @staticmethod
    def _normalize(value: str) -> str:
        return "\n".join(line.rstrip() for line in value.strip().splitlines())

    @staticmethod
    def _case_result(test_case_id: int, status: str, stdout: str, stderr: str,
                     started: float) -> CaseResult:
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
