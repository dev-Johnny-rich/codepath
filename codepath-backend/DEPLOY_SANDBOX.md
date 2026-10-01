# 独立生产沙箱节点

在与业务服务分离的 Linux 主机上安装 Docker，只部署 `sandbox-worker`。节点需能访问 Redis 和业务服务的内网地址。

```bash
docker compose -f docker-compose.sandbox-node.yml up -d --build --scale sandbox-worker=3
```

生产环境必须设置 `REDIS_HOST`、`REDIS_PASSWORD`、`BUSINESS_API_URL` 和 `INTERNAL_SERVICE_TOKEN`，并用防火墙限制 Redis/业务内部接口只对沙箱节点开放。宿主机不存放业务数据库密钥。

建议上线前预拉取 Python、Java 和 C++ 运行时镜像；Worker 也会在首次收到对应语言任务时自动拉取缺失镜像。更高风险的公网环境建议将 Docker 隔离升级为 gVisor、Kata Containers 或 Firecracker。
