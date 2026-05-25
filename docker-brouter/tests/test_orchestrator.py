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
