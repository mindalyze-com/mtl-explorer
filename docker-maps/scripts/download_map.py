#!/usr/bin/env python3
"""
download_map.py — Download the PMTiles planet file (resume-capable)

Usage:
    python3 download_map.py <target_path> <url|"latest">

Arguments:
    target_path  Path where the .pmtiles file will be saved  (e.g. /data/planet.pmtiles)
    url          Direct download URL, or "latest" to auto-resolve today's / recent daily build

Behaviour:
    • Skips entirely if the target file's .done sentinel exists.
    • Resumes via wget --continue if the file exists but the sentinel is missing.
    • Delegates to wget --continue so an interrupted download resumes where it left off.
    • "latest" mode resolves today's / recent daily build via common.resolve_latest_url().
"""

import os
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, os.path.dirname(__file__))
from common import die, log, resolve_latest_url, _HTTP_HEADERS


def main() -> None:
    if len(sys.argv) < 3:
        die(f"Usage: {sys.argv[0]} <target_path> <url|latest>")

    target = Path(sys.argv[1])
    url = sys.argv[2]
    done_file = target.with_name(target.name + ".done")
    url_file = target.with_name(target.name + ".url")

    # ── Already fully downloaded? ─────────────────────────────────────
    if done_file.exists():
        size_gb = target.stat().st_size / (1024 ** 3) if target.exists() else 0
        log(f"Planet file already complete: {target} ({size_gb:.1f} GB) — skipping download")
        return

    # ── Partial download present? ─────────────────────────────────────
    if target.exists():
        size_gb = target.stat().st_size / (1024 ** 3)
        log(f"Planet file present but sentinel missing — resuming incomplete download ({size_gb:.1f} GB so far)")

    # ── Resolve "latest" ─────────────────────────────────────────────
    if url == "latest":
        if url_file.exists():
            url = url_file.read_text().strip()
            log(f"Resuming with previously resolved 'latest' URL: {url}")
        else:
            url = resolve_latest_url()
            url_file.write_text(url)
            log(f"Saved resolved URL to {url_file}")

    if not url:
        die(f"No download URL provided and {target} does not exist.")

    # ── Download with resume support via wget ─────────────────────────
    log(f"Downloading planet file to {target}")
    log(f"  URL: {url}")
    log("  This is a large file (~120 GB). The download will resume if interrupted.")

    result = subprocess.run(
        [
            "wget",
            f"--user-agent={_HTTP_HEADERS['User-Agent']}",
            "--continue",
            "--show-progress",
            "--progress=bar:force:noscroll",
            "--timeout=60",
            "--waitretry=10",
            "--tries=0",
            "-O", str(target),
            url,
        ]
    )

    if result.returncode != 0:
        die(f"Download failed (exit code {result.returncode}). Re-run the container to resume.")

    done_file.touch()
    size_gb = target.stat().st_size / (1024 ** 3)
    log(f"Download complete: {target} ({size_gb:.1f} GB)")


if __name__ == "__main__":
    main()
