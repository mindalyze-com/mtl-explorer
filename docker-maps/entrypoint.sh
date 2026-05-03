#!/bin/sh
# ─────────────────────────────────────────────────────────────────────
# entrypoint.sh — Thin wrapper that delegates to the Python orchestrator.
#
# The orchestrator starts nginx immediately (healthy), then waits for
# a kickoff request from the Java backend carrying the scope
# (PUT /kickoff/prod for the application-managed local map service).
# ─────────────────────────────────────────────────────────────────────

exec python3 /app/scripts/orchestrator.py "$@"
