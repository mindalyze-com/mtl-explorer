#!/usr/bin/env python3
"""
orchestrator.py — top-level supervisor for the BRouter sidecar.

Responsibilities:
  1. Launch the BRouter HTTP router (Java) as a child process, restart on exit.
  2. Launch a tiny admin HTTP server on a second port with two endpoints:
       GET  /status   — JSON with segment-download progress + ready state
       POST /prewarm  — accepts {minLat,maxLat,minLng,maxLng}, enqueues downloads
  3. Expose a simple download queue that fetches missing rd5 segments from
     https://brouter.de/brouter/segments4/ into $BROUTER_SEGMENTS_DIR.

All numeric/path/URL values live in constants.py.
"""
import json
import logging
import os
import signal
import subprocess
import sys
import threading
import time
import urllib.request
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

sys.path.insert(0, os.path.dirname(__file__))
from constants import (
    ADMIN_BIND_HOST, ADMIN_CLIENT_TIMEOUT_SEC, ADMIN_POLL_INTERVAL_SEC, ADMIN_PORT,
    ADMIN_RESTART_DELAY_SEC, ADMIN_RESTART_FAILURE_LIMIT, ADMIN_RESTART_RESET_SEC,
    ADMIN_SHUTDOWN_TIMEOUT_SEC, BROUTER_HOME, BROUTER_PORT, BROUTER_RESTART_DELAY_SEC,
    BROUTER_VERSION, IMAGE_BUILD_TIME_FILE,
    IMAGE_VERSION_FILE, JAVA_ACTIVE_PROCESSORS, JAVA_MAX_RUNNING_TIME_SEC,
    JAVA_XMS, JAVA_XMX, JAVA_YOUNG_GENERATION, SEGMENTS_DIR, SIGTERM_WAIT_SEC,
    STATUS_FILE, STATUS_WRITE_INTERVAL_SEC, WATCHDOG_CHECK_INTERVAL_SEC,
    WATCHDOG_HEARTBEAT_STALE_SEC, WATCHDOG_HTTP_FAILURE_LIMIT, WATCHDOG_HTTP_TIMEOUT_SEC,
)
from segment_downloader import SegmentDownloader

logging.basicConfig(level=logging.INFO, format="[orchestrator] %(levelname)s %(message)s", stream=sys.stdout)
LOGGER = logging.getLogger("brouter.orchestrator")


def log(msg: str) -> None:
    LOGGER.info(msg)


def metadata_value(env_name: str, file_path: Path) -> str | None:
    value = os.environ.get(env_name, "").strip()
    if value:
        return value
    try:
        file_value = file_path.read_text().strip()
        return file_value or None
    except OSError as exc:
        LOGGER.warning("Image metadata read failed path=%s type=%s message=%s", file_path, type(exc).__name__, exc)
        return None


class AdminHTTPServer(HTTPServer):
    """Bound incomplete requests so they cannot block supervision indefinitely."""

    def get_request(self):
        connection, address = super().get_request()
        connection.settimeout(ADMIN_CLIENT_TIMEOUT_SEC)
        return connection, address

    def handle_error(self, request, client_address):
        exc = sys.exc_info()[1]
        LOGGER.warning("Admin request failed type=%s message=%s", type(exc).__name__, exc)
        LOGGER.debug("Admin request traceback", exc_info=True)


def non_blank(value: str | None) -> str | None:
    if value is None:
        return None
    stripped = value.strip()
    return stripped or None


def build_version_info() -> dict:
    components = {}
    brouter_version = non_blank(BROUTER_VERSION)
    if brouter_version:
        components["brouter"] = brouter_version

    return {
        "image": {
            "version": metadata_value("MTL_IMAGE_VERSION", IMAGE_VERSION_FILE),
            "buildTime": metadata_value("MTL_IMAGE_BUILD_TIME", IMAGE_BUILD_TIME_FILE),
        },
        "components": components,
        "data": {},
    }


class Supervisor:
    def __init__(self) -> None:
        self.downloader = SegmentDownloader(SEGMENTS_DIR)
        self.brouter_proc: subprocess.Popen | None = None
        self._shutdown = threading.Event()
        self._startup_complete = threading.Event()
        self._failure_lock = threading.Lock()
        self._failure_reason: str | None = None
        self._admin_server: AdminHTTPServer | None = None
        self._admin_thread: threading.Thread | None = None
        self._startup_thread: threading.Thread | None = None
        self._status_thread: threading.Thread | None = None
        self._download_thread: threading.Thread | None = None
        self._last_watchdog_tick = time.monotonic()
        self._admin_health_failures = 0

    def fail(self, reason: str) -> None:
        with self._failure_lock:
            if self._failure_reason is None:
                self._failure_reason = reason
                LOGGER.error("Supervision failed: %s; stopping for container restart", reason)
        self._shutdown.set()

    def start_worker(self, name: str, target) -> threading.Thread:
        def guarded():
            try:
                target()
                if not self._shutdown.is_set():
                    self.fail(f"Background worker {name} stopped unexpectedly")
            except Exception as exc:
                LOGGER.exception("Background worker failed name=%s type=%s message=%s", name, type(exc).__name__, exc)
                self.fail(f"Background worker {name} failed ({type(exc).__name__})")

        worker = threading.Thread(target=guarded, name=name, daemon=True)
        worker.start()
        return worker

    # ── BRouter process lifecycle ────────────────────────────────────

    def start_brouter(self) -> None:
        # BRouter's standard_server_3.sh launches com.routing.server.RouteServer.
        # We invoke java directly to keep control over args/env.
        java = "java"
        # The release zip produces brouter-<VERSION>-all.jar; fall back to brouter.jar.
        jar_candidates = list(BROUTER_HOME.glob("brouter-*-all.jar")) + [BROUTER_HOME / "brouter.jar"]
        cp_jar = str(next((j for j in jar_candidates if j.exists()), jar_candidates[-1]))
        profiles_dir = str(BROUTER_HOME / "profiles2")
        customprofiles_dir = str(BROUTER_HOME / "customprofiles")
        Path(customprofiles_dir).mkdir(parents=True, exist_ok=True)

        cmd = [
            java,
            f"-Xms{JAVA_XMS}",
            f"-Xmx{JAVA_XMX}",
            f"-Xmn{JAVA_YOUNG_GENERATION}",
            "-XX:+UseG1GC",
            f"-XX:ActiveProcessorCount={JAVA_ACTIVE_PROCESSORS}",
            "-XX:+ExitOnOutOfMemoryError",
            f"-DmaxRunningTime={JAVA_MAX_RUNNING_TIME_SEC}",
            "-cp", cp_jar,
            "btools.server.RouteServer",
            str(SEGMENTS_DIR),
            profiles_dir,
            customprofiles_dir,
            str(BROUTER_PORT),
            "1",  # max-threads
        ]
        log("Starting BRouter: " + " ".join(cmd))
        self.brouter_proc = subprocess.Popen(cmd, cwd=str(BROUTER_HOME))

    def watch_brouter(self) -> None:
        while not self._shutdown.is_set():
            self._last_watchdog_tick = time.monotonic()
            self.check_workers()
            if self._shutdown.is_set():
                break
            if self._startup_complete.is_set() and self.brouter_proc is not None:
                code = self.brouter_proc.poll()
                if code is not None:
                    LOGGER.warning("BRouter exited code=%s; restarting in %ss", code, BROUTER_RESTART_DELAY_SEC)
                    if self._shutdown.wait(BROUTER_RESTART_DELAY_SEC):
                        break
                    self.start_brouter()
            self.check_admin_health()
            self._shutdown.wait(WATCHDOG_CHECK_INTERVAL_SEC)

    def check_workers(self) -> None:
        workers = [("admin-http", self._admin_thread), ("status-writer", self._status_thread),
                   ("segment-downloader", self._download_thread)]
        if not self._startup_complete.is_set():
            workers.append(("routing-startup", self._startup_thread))
        for name, worker in workers:
            if worker is not None and not worker.is_alive():
                self.fail(f"Background worker {name} is no longer running")
                return

    def health_status(self) -> dict:
        reason = self._failure_reason
        if reason is None and time.monotonic() - self._last_watchdog_tick > WATCHDOG_HEARTBEAT_STALE_SEC:
            reason = "Supervisor heartbeat is stale"
        if reason is None and self._shutdown.is_set():
            reason = "Supervisor is shutting down"
        if reason is None and self._startup_complete.is_set():
            if self.brouter_proc is None or self.brouter_proc.poll() is not None:
                reason = "BRouter process is not running"
        return {"ok": reason is None, **({"reason": reason} if reason else {})}

    def check_admin_health(self) -> None:
        try:
            with urllib.request.urlopen(f"http://127.0.0.1:{ADMIN_PORT}/health", timeout=WATCHDOG_HTTP_TIMEOUT_SEC) as response:
                if json.load(response).get("ok") is not True:
                    raise ValueError("Admin health response is not healthy")
        except Exception as exc:
            self._admin_health_failures += 1
            LOGGER.warning("Admin health check failed count=%s/%s type=%s message=%s",
                           self._admin_health_failures, WATCHDOG_HTTP_FAILURE_LIMIT, type(exc).__name__, exc)
            LOGGER.debug("Admin health check traceback", exc_info=True)
            if self._admin_health_failures >= WATCHDOG_HTTP_FAILURE_LIMIT:
                self.fail("Admin HTTP service did not recover after consecutive health check failures")
        else:
            if self._admin_health_failures:
                log(f"Admin HTTP health recovered after {self._admin_health_failures} failed checks")
            self._admin_health_failures = 0

    # ── Status file ──────────────────────────────────────────────────

    def build_status(self) -> dict:
        storage_available = SEGMENTS_DIR.is_dir()
        status = {
            "available": storage_available,
            "brouterPort": BROUTER_PORT,
            "brouterRunning": self.brouter_proc is not None and self.brouter_proc.poll() is None,
            "segmentsDir": str(SEGMENTS_DIR),
            "versionInfo": build_version_info(),
        }
        if not storage_available:
            status["reason"] = (
                f"Routing data directory {SEGMENTS_DIR} is missing. "
                "Restore the BRouter segment directory and restart the routing service."
            )
        status.update(self.downloader.snapshot())
        return status

    def write_status_loop(self) -> None:
        while not self._shutdown.is_set():
            try:
                STATUS_FILE.write_text(json.dumps(self.build_status()))
            except Exception as exc:
                LOGGER.warning("Status file write failed type=%s message=%s", type(exc).__name__, exc)
                LOGGER.debug("Status file write traceback", exc_info=True)
            self._shutdown.wait(STATUS_WRITE_INTERVAL_SEC)

    # ── Admin HTTP server ────────────────────────────────────────────

    def start_admin_http(self) -> None:
        supervisor = self

        class Handler(BaseHTTPRequestHandler):
            def _send_json(self, code: int, body: dict) -> None:
                payload = json.dumps(body).encode("utf-8")
                self.send_response(code)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)

            def log_message(self, fmt, *args):  # noqa: N802
                LOGGER.debug("admin " + (fmt % args))

            def do_GET(self):  # noqa: N802
                if self.path.rstrip("/") == "/status":
                    self._send_json(200, supervisor.build_status())
                elif self.path.rstrip("/") == "/health":
                    health = supervisor.health_status()
                    self._send_json(200 if health["ok"] else 503, health)
                else:
                    self._send_json(404, {"error": "not found", "path": self.path})

            def do_POST(self):  # noqa: N802
                path = self.path.rstrip("/")
                if path not in ("/prewarm", "/prewarm-urgent"):
                    self._send_json(404, {"error": "not found", "path": self.path})
                    return
                urgent = path == "/prewarm-urgent"
                length = int(self.headers.get("Content-Length", "0"))
                raw = self.rfile.read(length) if length > 0 else b"{}"
                try:
                    payload = json.loads(raw.decode("utf-8"))
                except json.JSONDecodeError as e:
                    LOGGER.warning("Admin prewarm JSON invalid type=%s message=%s", type(e).__name__, e)
                    self._send_json(400, {"error": f"invalid json: {e}"})
                    return
                try:
                    queued = supervisor.downloader.enqueue_bbox(
                        min_lng=float(payload["minLng"]),
                        min_lat=float(payload["minLat"]),
                        max_lng=float(payload["maxLng"]),
                        max_lat=float(payload["maxLat"]),
                        urgent=urgent,
                    )
                    self._send_json(202, {"queued": queued, "urgent": urgent})
                except Exception as e:
                    LOGGER.warning("Admin prewarm request failed type=%s message=%s", type(e).__name__, e)
                    self._send_json(400, {"error": str(e)})

        def serve():
            failures = 0
            while not self._shutdown.is_set():
                started = time.monotonic()
                server = None
                try:
                    server = AdminHTTPServer((ADMIN_BIND_HOST, ADMIN_PORT), Handler)
                    self._admin_server = server
                    log(f"Admin HTTP listening on :{ADMIN_PORT}")
                    server.serve_forever(poll_interval=ADMIN_POLL_INTERVAL_SEC)
                    if not self._shutdown.is_set():
                        raise RuntimeError("Admin HTTP serve loop returned unexpectedly")
                except Exception as exc:
                    if time.monotonic() - started >= ADMIN_RESTART_RESET_SEC:
                        failures = 0
                    failures += 1
                    LOGGER.warning("Admin HTTP server failed attempt=%s/%s type=%s message=%s",
                                   failures, ADMIN_RESTART_FAILURE_LIMIT, type(exc).__name__, exc,
                                   exc_info=failures == 1)
                    LOGGER.debug("Admin HTTP server traceback", exc_info=True)
                    if failures >= ADMIN_RESTART_FAILURE_LIMIT:
                        self.fail("Admin HTTP server exceeded its recovery limit")
                finally:
                    if server is not None:
                        server.server_close()
                    self._admin_server = None
                if not self._shutdown.is_set():
                    log(f"Restarting admin HTTP server in {ADMIN_RESTART_DELAY_SEC}s")
                    self._shutdown.wait(ADMIN_RESTART_DELAY_SEC)

        self._admin_thread = self.start_worker("admin-http", serve)

    # ── Shutdown ─────────────────────────────────────────────────────

    def install_signal_handlers(self) -> None:
        def handler(signum, _frame):
            log(f"signal {signum} received — shutting down")
            self._shutdown.set()

        signal.signal(signal.SIGTERM, handler)
        signal.signal(signal.SIGINT, handler)

    def prepare_routing(self) -> None:
        started = time.monotonic()
        log("Routing startup validation started")
        self.downloader.validate_existing_segments()
        if self._shutdown.is_set():
            return
        self._download_thread = self.downloader.start()
        self.start_brouter()
        self._startup_complete.set()
        log(f"Routing startup completed durationSec={time.monotonic() - started:.3f}")

    def stop(self) -> None:
        self._shutdown.set()
        if self.brouter_proc is not None and self.brouter_proc.poll() is None:
            self.brouter_proc.terminate()
            try:
                self.brouter_proc.wait(timeout=SIGTERM_WAIT_SEC)
            except subprocess.TimeoutExpired as exc:
                LOGGER.warning("BRouter shutdown timed out type=%s message=%s; killing child", type(exc).__name__, exc)
                self.brouter_proc.kill()
                self.brouter_proc.wait(timeout=SIGTERM_WAIT_SEC)
        server = self._admin_server
        if server is not None:
            stopper = threading.Thread(target=server.shutdown, name="admin-shutdown", daemon=True)
            stopper.start()
            stopper.join(timeout=ADMIN_SHUTDOWN_TIMEOUT_SEC)
            if stopper.is_alive():
                LOGGER.warning("Admin HTTP shutdown did not finish within %ss", ADMIN_SHUTDOWN_TIMEOUT_SEC)
        for worker in (self._admin_thread, self._status_thread):
            if worker is not None:
                worker.join(timeout=ADMIN_SHUTDOWN_TIMEOUT_SEC)
                if worker.is_alive():
                    LOGGER.warning("Background worker still running during shutdown name=%s", worker.name)

    def run(self) -> int:
        started = time.monotonic()
        log("BRouter supervision started")
        try:
            SEGMENTS_DIR.mkdir(parents=True, exist_ok=True)
            self.install_signal_handlers()
            self.start_admin_http()
            self._status_thread = self.start_worker("status-writer", self.write_status_loop)
            self._startup_thread = threading.Thread(target=self.prepare_routing_guarded, name="routing-startup", daemon=True)
            self._startup_thread.start()
            self.watch_brouter()
        except Exception as exc:
            LOGGER.exception("Supervisor operation failed type=%s message=%s", type(exc).__name__, exc)
            self.fail(f"Supervisor operation failed ({type(exc).__name__})")
        finally:
            try:
                self.stop()
            except Exception as exc:
                LOGGER.exception("Supervisor shutdown failed type=%s message=%s", type(exc).__name__, exc)
                self.fail(f"Supervisor shutdown failed ({type(exc).__name__})")
            log(f"BRouter supervision stopped failed={self._failure_reason is not None} durationSec={time.monotonic() - started:.3f}")
        return 1 if self._failure_reason else 0

    def prepare_routing_guarded(self) -> None:
        try:
            self.prepare_routing()
        except Exception as exc:
            LOGGER.exception("Routing startup failed type=%s message=%s", type(exc).__name__, exc)
            self.fail(f"Routing startup failed ({type(exc).__name__})")


if __name__ == "__main__":
    raise SystemExit(Supervisor().run())
