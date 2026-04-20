# Run BRouter locally

```bash
# Build
docker build -t mytraillog-brouter:local docker-brouter

# Run
docker run -d --name brouter -p 17777:17777 -p 17778:17778 \
  -v brouter-segments:/segments4 \
  mytraillog-brouter:local

# Check
curl http://localhost:17778/status

# Stop
docker stop brouter && docker rm brouter
```

`application-dev.yml` — planner config pointing to local instance:
```yaml
planner:
  enabled: true
  brouter-base-url: http://localhost:17777
  status-url: http://localhost:17778/status
```
