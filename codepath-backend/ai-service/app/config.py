import os


class Settings:
    redis_host = os.getenv("REDIS_HOST", "localhost")
    redis_port = int(os.getenv("REDIS_PORT", "6380"))
    redis_password = os.getenv("REDIS_PASSWORD", "redis-dev")
    diagnosis_queue = os.getenv("DIAGNOSIS_QUEUE", "codepath:diagnosis:queue")
    business_api_url = os.getenv("BUSINESS_API_URL", "http://localhost:8081")
    internal_token = os.getenv("INTERNAL_SERVICE_TOKEN", "internal-dev")
    llm_base_url = os.getenv("LLM_BASE_URL", "")
    llm_api_key = os.getenv("LLM_API_KEY", "")
    llm_model = os.getenv("LLM_MODEL", "")
    llm_timeout_seconds = float(os.getenv("LLM_TIMEOUT_SECONDS", "45"))
    max_attempts = int(os.getenv("WORKER_MAX_ATTEMPTS", "3"))
    diagnosis_dlq = os.getenv("DIAGNOSIS_DLQ", "codepath:diagnosis:dlq")

    @property
    def llm_configured(self) -> bool:
        return bool(self.llm_base_url and self.llm_api_key and self.llm_model)


settings = Settings()
