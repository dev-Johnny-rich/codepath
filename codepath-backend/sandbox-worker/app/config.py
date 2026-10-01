import os


class Settings:
    redis_host = os.getenv("REDIS_HOST", "localhost")
    redis_port = int(os.getenv("REDIS_PORT", "6380"))
    redis_password = os.getenv("REDIS_PASSWORD", "redis-dev")
    queue_name = os.getenv("EXECUTION_QUEUE", "codepath:submission:queue")
    business_api_url = os.getenv("BUSINESS_API_URL", "http://localhost:8081")
    internal_token = os.getenv("INTERNAL_SERVICE_TOKEN", "internal-dev")
    python_image = os.getenv("SANDBOX_PYTHON_IMAGE", os.getenv("SANDBOX_IMAGE", "python:3.12-alpine"))
    java_image = os.getenv("SANDBOX_JAVA_IMAGE", "eclipse-temurin:21-jdk-alpine")
    cpp_image = os.getenv("SANDBOX_CPP_IMAGE", "gcc:14-bookworm")
    max_output_bytes = int(os.getenv("MAX_OUTPUT_BYTES", "65536"))
    max_attempts = int(os.getenv("WORKER_MAX_ATTEMPTS", "3"))
    dead_letter_queue = os.getenv("EXECUTION_DLQ", "codepath:submission:dlq")


settings = Settings()
