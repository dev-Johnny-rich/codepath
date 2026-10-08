def test_bkt_service_imports():
    import bkt_service  # noqa: F401


def test_worker_http_client_dependency_imports():
    import httpx

    assert httpx.__version__ == "0.28.1"
