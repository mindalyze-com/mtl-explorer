import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

SCRIPT_DIR = Path(__file__).resolve().parents[1] / "scripts"
sys.path.insert(0, str(SCRIPT_DIR))

import orchestrator  # noqa: E402


class OrchestratorStartupOrderTest(unittest.TestCase):
    def test_admin_and_validation_run_before_brouter_starts(self):
        events = []
        supervisor = orchestrator.Supervisor()
        supervisor.downloader = FakeDownloader(events)
        supervisor.install_signal_handlers = lambda: events.append("signals")
        supervisor.start_admin_http = lambda: events.append("admin")
        supervisor.write_status_loop = lambda: events.append("status-writer")
        supervisor.start_brouter = lambda: events.append("brouter")
        supervisor.watch_brouter = lambda: events.append("watch")

        with tempfile.TemporaryDirectory() as tmp:
            with patch.object(orchestrator, "SEGMENTS_DIR", Path(tmp)):
                with patch.object(orchestrator.threading, "Thread", ImmediateThread):
                    supervisor.run()

        self.assertEqual(
            ["signals", "admin", "status-writer", "validate", "downloader-start", "brouter", "watch"],
            events,
        )

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


class FakeDownloader:
    def __init__(self, events):
        self.events = events

    def validate_existing_segments(self):
        self.events.append("validate")

    def start(self):
        self.events.append("downloader-start")


class ImmediateThread:
    def __init__(self, target, name=None, daemon=None):
        self.target = target

    def start(self):
        self.target()


if __name__ == "__main__":
    unittest.main()
