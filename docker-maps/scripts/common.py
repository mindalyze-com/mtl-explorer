"""
common.py — Shared helpers for map-server scripts.
Sourced by importing: from common import log, die, PROTOMAPS_BUILDS_BASE_URL, resolve_latest_url
"""

import os
import sys
import time
import urllib.request
from datetime import date, timedelta

# ── Protomaps daily-build base URL ───────────────────────────────────
# Single source of truth — used by orchestrator.py, download_map.py,
# and referenced in shell scripts and Dockerfile comments.
# Override at runtime via env var to avoid rebuilding the container
# if the upstream URL ever changes again.
PROTOMAPS_BUILDS_BASE_URL = os.environ.get(
    "PROTOMAPS_BUILDS_BASE_URL", "https://build.protomaps.com"
)

# CDNs (e.g. Cloudflare) block the default Python-urllib User-Agent with 403.
_HTTP_HEADERS = {"User-Agent": "mytraillog-map-server/1.0"}


def protomaps_build_url(date_str: str) -> str:
    """Build the full download URL for a given YYYYMMDD date string."""
    return f"{PROTOMAPS_BUILDS_BASE_URL}/{date_str}.pmtiles"


def log(msg: str) -> None:
    print(f"[map-server] {time.strftime('%Y-%m-%d %H:%M:%S')} {msg}", flush=True)


def die(msg: str) -> None:
    log(f"ERROR: {msg}")
    sys.exit(1)


def resolve_latest_url(max_days: int = 60) -> str:
    """Probe Protomaps daily builds (today → max_days ago) and return the first available URL.

    Raises RuntimeError if no build is found.
    """
    log("MAP_DOWNLOAD_URL=latest — resolving most recent daily build ...")
    for days_ago in range(max_days):
        d = date.today() - timedelta(days=days_ago)
        url = protomaps_build_url(d.strftime("%Y%m%d"))
        log(f"  Checking {url} ...")
        try:
            req = urllib.request.Request(url, method="HEAD", headers=_HTTP_HEADERS)
            with urllib.request.urlopen(req, timeout=15):
                log(f"  Found: {url}")
                return url
        except Exception:
            continue
    raise RuntimeError(f"Could not resolve 'latest' — no daily build found for the last {max_days} days.")
