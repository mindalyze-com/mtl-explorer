#!/usr/bin/env python3
"""
start_server.py — Start the go-pmtiles tile server

Replaces the current process via os.execvp so it becomes PID 1
and receives Docker signals directly.

Usage:
    python3 start_server.py <data_dir> [port] [extra pmtiles args...]

Arguments:
    data_dir   Directory containing .pmtiles files  (e.g. /data)
    port       Port to listen on                    (default: 8082 internal, nginx proxies 8081)
"""

import os
import sys
from pathlib import Path

sys.path.insert(0, os.path.dirname(__file__))
from common import die, log


def main() -> None:
    if len(sys.argv) < 2:
        die(f"Usage: {sys.argv[0]} <data_dir> [port] [extra args...]")

    data_dir = Path(sys.argv[1])
    port = sys.argv[2] if len(sys.argv) > 2 else "8082"
    extra = sys.argv[3:] if len(sys.argv) > 3 else []

    if not data_dir.is_dir():
        die(f"Data directory not found: {data_dir}")

    log("Starting pmtiles tile server")
    log(f"  Data dir : {data_dir}")
    log(f"  Port     : {port}")
    log(f"  CORS     : *")

    # execvp replaces this process — pmtiles receives Docker signals directly
    os.execvp(
        "pmtiles",
        ["pmtiles", "serve", str(data_dir), f"--port={port}", "--cors=*"] + extra,
    )


if __name__ == "__main__":
    main()
