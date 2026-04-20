#!/bin/sh
# ─────────────────────────────────────────────────────────────────────
# entrypoint.sh — Thin wrapper that delegates to the Python orchestrator.
#
# The orchestrator starts nginx immediately (healthy), then waits for
# a kickoff request from the Java backend carrying the scope
# (PUT /kickoff/demo or PUT /kickoff/prod). Seeding of pre-built tiles
# from /data-prebuilt/{scope}/ into /data/{scope}/ happens inside the
# orchestrator on each kickoff.
# ─────────────────────────────────────────────────────────────────────

exec python3 /app/scripts/orchestrator.py "$@"
