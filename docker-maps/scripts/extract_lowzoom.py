#!/usr/bin/env python3
"""
extract_lowzoom.py — Extract a small low-zoom PMTiles file

Produces a ~100 MB file containing only zoom levels 0–N, suitable for
downloading into the browser for offline map display.

Usage:
    python3 extract_lowzoom.py <source> <target> [maxzoom]

Arguments:
    source   Source .pmtiles file     (e.g. /data/planet.pmtiles)
    target   Target low-zoom file     (e.g. /data/world-lowzoom.pmtiles)
    maxzoom  Maximum zoom level       (default: 8)
"""

import os
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, os.path.dirname(__file__))
from common import die, log


def main() -> None:
    if len(sys.argv) < 3:
        die(f"Usage: {sys.argv[0]} <source> <target> [maxzoom]")

    source = Path(sys.argv[1])
    target = Path(sys.argv[2])
    maxzoom = sys.argv[3] if len(sys.argv) > 3 else "8"

    # ── Already extracted? ────────────────────────────────────────────
    if target.exists():
        size_mb = target.stat().st_size / (1024 ** 2)
        log(f"Low-zoom file already present: {target} ({size_mb:.0f} MB)")
        return

    # ── Validate source ───────────────────────────────────────────────
    if not source.exists():
        die(f"Source file not found: {source}")

    # ── Extract ───────────────────────────────────────────────────────
    log(f"Extracting low-zoom tiles (zoom 0-{maxzoom}) ...")
    log(f"  Source: {source}")
    log(f"  Target: {target}")

    result = subprocess.run(
        ["pmtiles", "extract", str(source), str(target), f"--maxzoom={maxzoom}"]
    )

    if result.returncode != 0:
        die(f"pmtiles extract failed (exit code {result.returncode})")

    size_mb = target.stat().st_size / (1024 ** 2)
    log(f"Low-zoom extract complete: {target} ({size_mb:.0f} MB)")


if __name__ == "__main__":
    main()
