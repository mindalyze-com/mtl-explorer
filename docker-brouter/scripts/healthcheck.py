#!/usr/bin/env python3
"""Check admin HTTP responsiveness and the supervisor heartbeat."""
import json
import sys
import urllib.request

from constants import ADMIN_PORT, WATCHDOG_HTTP_TIMEOUT_SEC


def main() -> int:
    try:
        with urllib.request.urlopen(f"http://127.0.0.1:{ADMIN_PORT}/health", timeout=WATCHDOG_HTTP_TIMEOUT_SEC) as response:
            if json.load(response).get("ok") is not True:
                raise ValueError("Supervisor health response is not healthy")
    except Exception as exc:
        print(f"BRouter health check failed type={type(exc).__name__} message={exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
