import errno
import io
import json
import socket
import sys
import tempfile
import threading
import time
import unittest
import urllib.error
import urllib.request
from pathlib import Path
from unittest.mock import Mock, patch

SCRIPT_DIR = Path(__file__).resolve().parents[1] / "scripts"
sys.path.insert(0, str(SCRIPT_DIR))

import orchestrator  # noqa: E402
import healthcheck  # noqa: E402


class OrchestratorStartupOrderTest(unittest.TestCase):
    def test_status_reports_missing_storage_and_recovers_when_restored(self):
        with tempfile.TemporaryDirectory() as tmp:
            segments_dir = Path(tmp) / "segments4"
            with patch.object(orchestrator, "SEGMENTS_DIR", segments_dir):
                supervisor = orchestrator.Supervisor()
                status = supervisor.build_status()
                self.assertFalse(status["available"])
                self.assertIn(str(segments_dir), status["reason"])
                self.assertIn("restart", status["reason"])
                segments_dir.mkdir()
                restored = supervisor.build_status()
                self.assertTrue(restored["available"])
                self.assertNotIn("reason", restored)
                segments_dir.rmdir()
                self.assertFalse(supervisor.build_status()["available"])

    def test_brouter_starts_with_memory_and_long_route_tuning(self):
        with tempfile.TemporaryDirectory() as tmp:
            brouter_home = Path(tmp)
            with patch.object(orchestrator, "BROUTER_HOME", brouter_home):
                with patch.object(orchestrator.subprocess, "Popen") as popen:
                    supervisor = orchestrator.Supervisor()
                    supervisor.start_brouter()

        command = popen.call_args.args[0]
        self.assertIn(f"-Xms{orchestrator.JAVA_XMS}", command)
        self.assertIn(f"-Xmx{orchestrator.JAVA_XMX}", command)
        self.assertIn(f"-Xmn{orchestrator.JAVA_YOUNG_GENERATION}", command)
        self.assertIn("-XX:+UseG1GC", command)
        self.assertIn(
            f"-XX:ActiveProcessorCount={orchestrator.JAVA_ACTIVE_PROCESSORS}",
            command,
        )
        self.assertIn("-XX:+ExitOnOutOfMemoryError", command)
        self.assertIn(
            f"-DmaxRunningTime={orchestrator.JAVA_MAX_RUNNING_TIME_SEC}",
            command,
        )
        self.assertEqual("1", command[-1])
        popen.assert_called_once_with(command, cwd=str(brouter_home))


class SupervisionTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        with socket.socket() as listener:
            listener.bind(("127.0.0.1", 0))
            self.port = listener.getsockname()[1]
        self.patches = [
            patch.object(orchestrator, "ADMIN_PORT", self.port),
            patch.object(healthcheck, "ADMIN_PORT", self.port),
            patch.object(orchestrator, "SEGMENTS_DIR", Path(self.tmp.name)),
            patch.object(orchestrator, "STATUS_FILE", Path(self.tmp.name) / "status.json"),
            patch.object(orchestrator, "ADMIN_RESTART_DELAY_SEC", 0.01),
            patch.object(orchestrator, "ADMIN_POLL_INTERVAL_SEC", 0.01),
            patch.object(orchestrator, "WATCHDOG_CHECK_INTERVAL_SEC", 0.02),
        ]
        for p in self.patches:
            p.start()
        self.supervisor = orchestrator.Supervisor()

    def tearDown(self):
        self.supervisor.stop()
        for p in reversed(self.patches):
            p.stop()
        self.tmp.cleanup()

    def wait_for(self, predicate, timeout=3):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if predicate():
                return
            time.sleep(0.01)
        self.fail("Timed out waiting for supervision condition")

    def request(self, path="/health"):
        with urllib.request.urlopen(f"http://127.0.0.1:{self.port}{path}", timeout=1) as response:
            return json.load(response)

    def test_admin_recovers_from_observed_efault_and_releases_old_socket(self):
        servers = []
        original = orchestrator.AdminHTTPServer

        class FailOnce(original):
            def serve_forever(server, **kwargs):
                servers.append(server)
                if len(servers) == 1:
                    raise OSError(errno.EFAULT, "Bad address")
                return super().serve_forever(**kwargs)

        with patch.object(orchestrator, "AdminHTTPServer", FailOnce):
            with self.assertLogs(orchestrator.LOGGER, level="INFO") as captured:
                self.supervisor.start_admin_http()
                self.wait_for(lambda: len(servers) == 2)
                self.assertTrue(self.request()["ok"])
                self.assertTrue(self.request("/status")["available"])
            self.assertEqual(-1, servers[0].socket.fileno())
            self.assertIsNone(self.supervisor._failure_reason)
            self.assertIn("Bad address", "\n".join(captured.output))
            self.assertIn("Restarting admin HTTP", "\n".join(captured.output))

    def test_repeated_admin_crashes_exhaust_recovery_and_stop_supervision(self):
        original = orchestrator.AdminHTTPServer
        failures = []

        class AlwaysFails(original):
            def serve_forever(server, **kwargs):
                failures.append(server)
                raise OSError(errno.EFAULT, "Bad address")

        with patch.object(orchestrator, "AdminHTTPServer", AlwaysFails):
            with self.assertLogs(orchestrator.LOGGER, level="WARNING"):
                self.supervisor.start_admin_http()
                self.assertTrue(self.supervisor._shutdown.wait(2))
                self.supervisor._admin_thread.join(1)
        self.assertEqual(orchestrator.ADMIN_RESTART_FAILURE_LIMIT, len(failures))
        self.assertIn("recovery limit", self.supervisor._failure_reason)
        self.assertTrue(all(server.socket.fileno() == -1 for server in failures))

    def test_failed_health_checks_are_consecutive_and_eventually_stop_service(self):
        unavailable = urllib.error.URLError("unresponsive admin HTTP")
        with self.assertLogs(orchestrator.LOGGER, level="WARNING"):
            with patch.object(orchestrator.urllib.request, "urlopen", side_effect=unavailable):
                self.supervisor.check_admin_health()
        with patch.object(orchestrator.urllib.request, "urlopen", return_value=io.BytesIO(b'{"ok":true}')):
            self.supervisor.check_admin_health()
        self.assertEqual(0, self.supervisor._admin_health_failures)
        with self.assertLogs(orchestrator.LOGGER, level="WARNING"):
            with patch.object(orchestrator.urllib.request, "urlopen", side_effect=unavailable):
                for _ in range(orchestrator.WATCHDOG_HTTP_FAILURE_LIMIT):
                    self.supervisor.check_admin_health()
        self.assertTrue(self.supervisor._shutdown.is_set())
        self.assertIn("health check failures", self.supervisor._failure_reason)

    def test_dead_download_worker_is_detected(self):
        self.supervisor._download_thread = Mock(is_alive=Mock(return_value=False))
        with self.assertLogs(orchestrator.LOGGER, level="ERROR"):
            self.supervisor.check_workers()
        self.assertIn("segment-downloader", self.supervisor._failure_reason)

    def test_stale_supervisor_heartbeat_fails_real_http_and_image_healthcheck(self):
        self.supervisor.start_admin_http()
        self.wait_for(lambda: self.supervisor._admin_server is not None)
        self.assertEqual(0, healthcheck.main())
        self.supervisor._last_watchdog_tick -= orchestrator.WATCHDOG_HEARTBEAT_STALE_SEC + 1
        with self.assertRaises(urllib.error.HTTPError) as failed:
            self.request()
        self.assertEqual(503, failed.exception.code)
        self.assertIn("heartbeat", json.load(failed.exception)["reason"])
        failed.exception.close()
        with patch.object(healthcheck.sys, "stderr", io.StringIO()) as stderr:
            self.assertEqual(1, healthcheck.main())
        self.assertIn("HTTPError", stderr.getvalue())

    def test_incomplete_client_request_times_out_without_losing_admin_service(self):
        with patch.object(orchestrator, "ADMIN_CLIENT_TIMEOUT_SEC", 0.1):
            self.supervisor.start_admin_http()
            self.wait_for(lambda: self.supervisor._admin_server is not None)
            with socket.create_connection(("127.0.0.1", self.port), timeout=1) as client:
                client.sendall(b"GET /health HTTP/1.1\r\n")
                self.assertTrue(self.request()["ok"])
            self.assertIsNone(self.supervisor._failure_reason)

    def test_watchdog_is_active_during_validation_and_java_waits_for_validation(self):
        entered = threading.Event()
        release = threading.Event()
        exit_codes = []
        process = Mock(poll=Mock(return_value=None))

        def validate():
            entered.set()
            release.wait(3)

        def start_java():
            self.supervisor.brouter_proc = process

        self.supervisor.downloader.validate_existing_segments = validate
        self.supervisor.downloader.start = Mock(return_value=Mock(is_alive=Mock(return_value=True)))
        with patch.object(self.supervisor, "install_signal_handlers"):
            with patch.object(self.supervisor, "start_brouter", side_effect=start_java) as java:
                with patch.object(self.supervisor, "check_admin_health", wraps=self.supervisor.check_admin_health) as checks:
                    runner = threading.Thread(target=lambda: exit_codes.append(self.supervisor.run()))
                    runner.start()
                    try:
                        self.assertTrue(entered.wait(2))
                        self.wait_for(lambda: checks.call_count >= 2)
                        java.assert_not_called()
                        self.assertTrue(self.request()["ok"])
                        release.set()
                        self.assertTrue(self.supervisor._startup_complete.wait(2))
                        java.assert_called_once()
                    finally:
                        release.set()
                        self.supervisor._shutdown.set()
                        runner.join(3)
                    self.assertFalse(runner.is_alive())
        self.assertEqual([0], exit_codes)
        process.terminate.assert_called_once()

    def test_startup_exception_returns_failure_for_container_restart(self):
        self.supervisor.downloader.validate_existing_segments = Mock(side_effect=RuntimeError("validation failed"))
        with patch.object(self.supervisor, "install_signal_handlers"):
            with self.assertLogs(orchestrator.LOGGER, level="ERROR"):
                self.assertEqual(1, self.supervisor.run())
        self.assertIn("Routing startup failed", self.supervisor._failure_reason)


if __name__ == "__main__":
    unittest.main()
