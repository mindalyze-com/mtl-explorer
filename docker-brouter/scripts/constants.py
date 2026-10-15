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
IMAGE_VERSION_FILE = Path("/opt/mtl/image-version")
IMAGE_BUILD_TIME_FILE = Path("/opt/mtl/image-build-time")

# Networking
BROUTER_PORT = int(os.environ.get("BROUTER_PORT", "17777"))
ADMIN_PORT = int(os.environ.get("BROUTER_ADMIN_PORT", "17778"))
ADMIN_BIND_HOST = "0.0.0.0"
ADMIN_CLIENT_TIMEOUT_SEC = 5.0
ADMIN_POLL_INTERVAL_SEC = 0.5
ADMIN_RESTART_DELAY_SEC = 1.0
ADMIN_RESTART_FAILURE_LIMIT = 3
ADMIN_RESTART_RESET_SEC = 60.0
ADMIN_SHUTDOWN_TIMEOUT_SEC = ADMIN_CLIENT_TIMEOUT_SEC + 1.0

# Supervision is active while routing segments are validated and downloaded.
WATCHDOG_CHECK_INTERVAL_SEC = 5.0
WATCHDOG_HTTP_TIMEOUT_SEC = 2.0
WATCHDOG_HTTP_FAILURE_LIMIT = 3
WATCHDOG_HEARTBEAT_STALE_SEC = 20.0
BROUTER_RESTART_DELAY_SEC = 3.0
STATUS_WRITE_INTERVAL_SEC = 2.0

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

# Java startup for the single-threaded route server. BRouter's upstream
# standalone script uses a fixed 128 MiB heap, an 8 MiB young generation, and
# a five-minute request timeout so long routes can complete.
JAVA_XMX = os.environ.get("BROUTER_JAVA_XMX", "128m")
JAVA_XMS = os.environ.get("BROUTER_JAVA_XMS", JAVA_XMX)
JAVA_YOUNG_GENERATION = os.environ.get("BROUTER_JAVA_YOUNG_GENERATION", "8m")
JAVA_ACTIVE_PROCESSORS = int(os.environ.get("BROUTER_JAVA_ACTIVE_PROCESSORS", "2"))
JAVA_MAX_RUNNING_TIME_SEC = int(os.environ.get("BROUTER_JAVA_MAX_RUNNING_TIME_SEC", "300"))
BROUTER_VERSION = os.environ.get("BROUTER_VERSION", "")

# Graceful shutdown
SIGTERM_WAIT_SEC = 10
