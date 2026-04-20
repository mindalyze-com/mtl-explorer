#!/usr/bin/env bash
# entrypoint — starts the orchestrator which spawns BRouter + admin HTTP server.
set -euo pipefail
exec python3 /opt/brouter-orchestrator/orchestrator.py
