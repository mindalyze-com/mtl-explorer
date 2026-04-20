#!/usr/bin/env python3
"""
Central constants for the BRouter sidecar orchestrator. Every magic value lives
here so the rest of the scripts stay clean and greppable.
"""
import os
from pathlib import Path

# Paths
BROUTER_HOME = Path(os.environ.get("BROUTER_HOME", "/opt/brouter"))
SEGMENTS_DIR = Path(os.environ.get("BROUTER_SEGMENTS_DIR", "/segments4"))
STATUS_FILE = Path("/tmp/brouter-status.json")

# Networking
BROUTER_PORT = int(os.environ.get("BROUTER_PORT", "17777"))
ADMIN_PORT = int(os.environ.get("BROUTER_ADMIN_PORT", "17778"))

# BRouter routing tile grid — each rd5 file covers a 5°×5° cell.
SEGMENT_DEGREES = 5

# Public rd5 mirror maintained by BRouter upstream.
SEGMENTS_BASE_URL = os.environ.get(
    "BROUTER_SEGMENTS_BASE_URL",
    "https://brouter.de/brouter/segments4"
)

# HTTP
HTTP_USER_AGENT = "mytraillog-brouter-sidecar/1.0"
DOWNLOAD_TIMEOUT_SEC = 300
DOWNLOAD_RETRIES = 3

# Java startup — tuned for RPi/NAS (256 MB default is BRouter's recommendation).
JAVA_XMX = os.environ.get("BROUTER_JAVA_XMX", "512m")

# Graceful shutdown
SIGTERM_WAIT_SEC = 10
