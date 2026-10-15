# Run BRouter locally

```bash
# Build
docker build -t mytraillog-brouter:local docker-brouter

# Run
docker run -d --name brouter -p 17777:17777 -p 17778:17778 \
  --memory 512m --memory-swap 512m \
  -v brouter-segments:/segments4 \
  mytraillog-brouter:local

# Check
curl http://localhost:17778/status

# Stop
docker stop brouter && docker rm brouter
```

For a backend running in the development container, use addresses reachable
from that container. The configuration is nested under `mtl`:
```yaml
mtl:
  planner:
    enabled: true
    brouter-base-url: http://host.docker.internal:17777
    status-url: http://host.docker.internal:17778/status
    brouter-timeout-ms: 310000
```

Docker Desktop/OrbStack provide `host.docker.internal`. On another Docker host,
use a configured host gateway or shared-network service name. `localhost` inside
the backend container refers to that container. See [sidecar settings](README.md).
