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
import os
import signal
import subprocess
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

sys.path.insert(0, os.path.dirname(__file__))
from constants import (
    ADMIN_PORT, BROUTER_HOME, BROUTER_PORT, JAVA_XMX, SEGMENTS_DIR,
    SIGTERM_WAIT_SEC, STATUS_FILE,
)
from segment_downloader import SegmentDownloader


def log(msg: str) -> None:
    print(f"[orchestrator] {msg}", flush=True)


class Supervisor:
    def __init__(self) -> None:
        self.downloader = SegmentDownloader(SEGMENTS_DIR)
        self.brouter_proc: subprocess.Popen | None = None
        self._shutdown = threading.Event()

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
            f"-Xmx{JAVA_XMX}",
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
            if self.brouter_proc is None:
                time.sleep(1.0)
                continue
            code = self.brouter_proc.poll()
            if code is None:
                time.sleep(1.0)
                continue
            if self._shutdown.is_set():
                return
            log(f"BRouter exited with code={code} — restarting in 3s")
            time.sleep(3.0)
            self.start_brouter()

    # ── Status file ──────────────────────────────────────────────────

    def build_status(self) -> dict:
        status = {
            "available": True,
            "brouterPort": BROUTER_PORT,
            "brouterRunning": self.brouter_proc is not None and self.brouter_proc.poll() is None,
            "segmentsDir": str(SEGMENTS_DIR),
        }
        status.update(self.downloader.snapshot())
        return status

    def write_status_loop(self) -> None:
        while not self._shutdown.is_set():
            try:
                STATUS_FILE.write_text(json.dumps(self.build_status()))
            except Exception as e:
                log(f"status write failed: {e}")
            time.sleep(2.0)

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
                log("admin " + (fmt % args))

            def do_GET(self):  # noqa: N802
                if self.path.rstrip("/") == "/status":
                    self._send_json(200, supervisor.build_status())
                elif self.path.rstrip("/") == "/health":
                    self._send_json(200, {"ok": True})
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
                    self._send_json(400, {"error": str(e)})

        server = HTTPServer(("0.0.0.0", ADMIN_PORT), Handler)
        log(f"Admin HTTP listening on :{ADMIN_PORT}")
        threading.Thread(target=server.serve_forever, name="admin-http", daemon=True).start()

    # ── Shutdown ─────────────────────────────────────────────────────

    def install_signal_handlers(self) -> None:
        def handler(signum, _frame):
            log(f"signal {signum} received — shutting down")
            self._shutdown.set()
            if self.brouter_proc and self.brouter_proc.poll() is None:
                self.brouter_proc.terminate()
                try:
                    self.brouter_proc.wait(timeout=SIGTERM_WAIT_SEC)
                except subprocess.TimeoutExpired:
                    self.brouter_proc.kill()
            sys.exit(0)

        signal.signal(signal.SIGTERM, handler)
        signal.signal(signal.SIGINT, handler)

    def run(self) -> None:
        SEGMENTS_DIR.mkdir(parents=True, exist_ok=True)
        self.install_signal_handlers()
        self.downloader.start()
        self.start_admin_http()
        self.start_brouter()
        threading.Thread(target=self.write_status_loop, name="status-writer", daemon=True).start()
        self.watch_brouter()


if __name__ == "__main__":
    Supervisor().run()
