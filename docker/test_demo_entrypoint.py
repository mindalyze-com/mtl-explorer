"""Container startup integration tests. Set TMPDIR to external task storage."""
import hashlib
import os
import subprocess
import tempfile
import time
import unittest
import uuid
from pathlib import Path

APP_IMAGE = os.environ.get("DEMO_PHOTO_TEST_IMAGE", "mtl-demo-photo-startup:local")
ENTRYPOINT = Path(__file__).with_name("my-entrypoint.sh").resolve()
GENERATOR = ENTRYPOINT.parent / "gpx_porto_taxi_dataset" / "generate_demo_photos.py"
DEMO_LOGGING = GENERATOR.with_name("demo_logging.py")


class DemoEntrypointTest(unittest.TestCase):
    def run_startup(self, mode, count="0", deadline="600", signal=None, valid_track=True,
                    stalled_generator=False):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            gpx = root / "gpx" / "demo_gpx_porto_taxi_dataset"
            gpx.mkdir(parents=True)
            (root / "media").mkdir()
            (gpx / "synthetic.gpx").write_text(
                '<gpx><trk><trkseg><trkpt lat="41.15" lon="-8.62"><time>2026-01-01T10:00:00Z</time></trkpt>'
                '<trkpt lat="41.16" lon="-8.61"><time>2026-01-01T10:10:00Z</time></trkpt></trkseg></trk></gpx>'
                if valid_track else "invalid XML")
            entrypoint, generator = ENTRYPOINT, GENERATOR
            if stalled_generator:
                entrypoint = root / "entrypoint.sh"
                entrypoint.write_text(ENTRYPOINT.read_text().replace(
                    "readonly DEMO_PHOTO_HARD_TIMEOUT_SECONDS=600",
                    "readonly DEMO_PHOTO_HARD_TIMEOUT_SECONDS=1"))
                generator = root / "generate_demo_photos.py"
                generator.write_text("import signal,time\nsignal.signal(signal.SIGTERM,signal.SIG_IGN)\n"
                                     "print('Stalled generator',flush=True)\ntime.sleep(600)\n")
            name = f"mtl-demo-startup-test-{uuid.uuid4().hex[:8]}"
            demo_environment = [] if mode is None else ["-e", f"DEMO_MODE={mode}"]
            volume_mode = "" if mode else ":ro"
            command = ["docker", "run", "-d", "--restart=no", "--name", name, "--entrypoint", "/bin/bash",
                       "-v", f"{entrypoint}:/my-entrypoint.sh:ro", "-v", f"{root}/gpx:/app/gpx{volume_mode}",
                       "-v", f"{generator}:/app/demo/generate_demo_photos.py:ro",
                       "-v", f"{DEMO_LOGGING}:/app/demo/demo_logging.py:ro",
                       "-v", f"{root}/media:/app/media{volume_mode}", *demo_environment,
                       "-e", f"DEMO_PHOTO_COUNT={count}", "-e", f"DEMO_PHOTO_GENERATION_TIMEOUT_SECONDS={deadline}",
                       APP_IMAGE, "/my-entrypoint.sh", "python3", "-c",
                       "import os,sys; from pathlib import Path; "
                       "assert not any(any(marker in (path/'cmdline').read_bytes() for marker in "
                       "(b'generate_demo_photos.py', b'resource_tracker', b'forkserver')) "
                       "for path in Path('/proc').iterdir() if path.name.isdigit() "
                       "and path.name != str(os.getpid())); print('JAVA_SENTINEL',sys.argv)"]
            started = time.monotonic()
            subprocess.run(command, check=True, stdout=subprocess.DEVNULL)
            try:
                if signal:
                    limit = time.monotonic() + 10
                    while not list((root / "media").glob("demo-photos/*.jpg")):
                        self.assertLess(time.monotonic(), limit)
                        time.sleep(0.05)
                    subprocess.run(["docker", "kill", "--signal", signal, name], check=True, stdout=subprocess.DEVNULL)
                result = subprocess.run(["docker", "wait", name], check=True, capture_output=True,
                                        text=True, timeout=22)
                logs = subprocess.run(["docker", "logs", name], check=True, capture_output=True, text=True)
                if not mode:
                    self.assertEqual([], list((root / "media").iterdir()))
                return int(result.stdout), logs.stdout + logs.stderr, time.monotonic() - started
            finally:
                subprocess.run(["docker", "stop", "-t", "1", name], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                subprocess.run(["docker", "container", "rm", name], stdout=subprocess.DEVNULL, check=True)

    def test_non_demo_starts_without_delay_or_demo_preparation(self):
        for mode in (None, ""):
            with self.subTest(mode=mode):
                result, logs, elapsed = self.run_startup(mode, count="20000")
                self.assertEqual(0, result)
                self.assertIn("JAVA_SENTINEL", logs)
                self.assertNotIn("spring.profiles.active=demo", logs)
                self.assertNotIn("Demo photo generation", logs)
                self.assertNotIn("startup allowance", logs)
                self.assertLess(elapsed, 10)

    def test_completed_demo_survives_repeated_stop_start_without_regeneration(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            gpx = root / "gpx" / "demo_gpx_porto_taxi_dataset"
            gpx.mkdir(parents=True)
            (root / "media").mkdir()
            (gpx / "synthetic.gpx").write_text(
                '<gpx><trk><trkseg><trkpt lat="41.15" lon="-8.62"><time>2026-01-01T10:00:00Z</time></trkpt>'
                '<trkpt lat="41.16" lon="-8.61"><time>2026-01-01T10:10:00Z</time></trkpt></trkseg></trk></gpx>')
            name = f"mtl-demo-restart-test-{uuid.uuid4().hex[:8]}"
            application = (
                "import os,signal,sys,time\nfrom pathlib import Path\n"
                "assert not any(any(marker in (path/'cmdline').read_bytes() for marker in "
                "(b'generate_demo_photos.py', b'resource_tracker', b'forkserver')) "
                "for path in Path('/proc').iterdir() if path.name.isdigit() "
                "and path.name != str(os.getpid()))\n"
                "signal.signal(signal.SIGTERM, lambda *_: sys.exit(0))\n"
                "print('JAVA_SENTINEL',sys.argv,flush=True)\n"
                "while True: time.sleep(1)\n")
            subprocess.run(
                ["docker", "run", "-d", "--restart=no", "--name", name, "--entrypoint", "/bin/bash",
                 "-v", f"{ENTRYPOINT}:/my-entrypoint.sh:ro", "-v", f"{root}/gpx:/app/gpx",
                 "-v", f"{GENERATOR}:/app/demo/generate_demo_photos.py:ro",
                 "-v", f"{DEMO_LOGGING}:/app/demo/demo_logging.py:ro",
                 "-v", f"{root}/media:/app/media", "-e", "DEMO_MODE=Y",
                 "-e", "DEMO_PHOTO_COUNT=4",
                 APP_IMAGE, "/my-entrypoint.sh", "python3", "-c", application],
                check=True, stdout=subprocess.DEVNULL)
            original = None
            try:
                for startup in range(3):
                    if startup:
                        subprocess.run(["docker", "start", name], check=True, stdout=subprocess.DEVNULL)
                    limit = time.monotonic() + 22
                    while True:
                        logs = subprocess.run(["docker", "logs", name], check=True,
                                              capture_output=True, text=True).stdout
                        if logs.count("JAVA_SENTINEL") == startup + 1:
                            break
                        self.assertLess(time.monotonic(), limit, logs)
                        time.sleep(0.2)
                    photos = sorted((root / "media" / "demo-photos").glob("*.jpg"))
                    self.assertEqual(4, len(photos))
                    snapshot = {path.name: (hashlib.sha256(path.read_bytes()).hexdigest(),
                                            path.stat().st_mtime_ns, path.stat().st_ino)
                                for path in photos}
                    progress_files = list((root / "media" / "demo-photos").glob(".demo-photo-format-v*.progress"))
                    self.assertEqual(1, len(progress_files))
                    progress = progress_files[0]
                    self.assertEqual("4", progress.read_text().strip())
                    snapshot[progress.name] = (progress.read_bytes(), progress.stat().st_mtime_ns)
                    if original is None:
                        original = snapshot
                    else:
                        self.assertEqual(original, snapshot)
                    subprocess.run(["docker", "stop", "-t", "2", name], check=True,
                                   stdout=subprocess.DEVNULL)
                    result = subprocess.run(["docker", "inspect", name, "--format", "{{.State.ExitCode}}"],
                                            check=True, capture_output=True, text=True)
                    self.assertEqual("0", result.stdout.strip())
                self.assertEqual(1, logs.count("Demo photo generation starting:"))
                self.assertIn("missing=4, workers=4,", logs)
                self.assertIn("count=4, workers=4,", logs)
                self.assertEqual(2, logs.count("skipping catalog loading and rendering."))
            finally:
                subprocess.run(["docker", "stop", "-t", "1", name],
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                subprocess.run(["docker", "container", "rm", name], check=True, stdout=subprocess.DEVNULL)

    def test_zero_count_starts_java_with_demo_profile_without_delay(self):
        result, logs, elapsed = self.run_startup("true")
        self.assertEqual(0, result)
        self.assertIn("disabled (count=0)", logs)
        self.assertIn("spring.profiles.active=demo", logs)
        self.assertNotIn("startup allowance", logs)
        self.assertLess(elapsed, 10)

    def test_failure_and_deadline_start_java(self):
        for valid, deadline in ((False, "600"), (True, "1")):
            result, logs, elapsed = self.run_startup("true", "20000", deadline, valid_track=valid)
            self.assertEqual(0, result)
            self.assertIn("failed or timed out; Java will start", logs)
            self.assertIn("JAVA_SENTINEL", logs)
            self.assertLess(elapsed, 20)

    def test_shutdown_during_generation_reaps_workers_without_starting_java(self):
        for signal, expected in (("TERM", 143), ("INT", 130)):
            result, logs, elapsed = self.run_startup("true", "20000", signal=signal)
            self.assertEqual(expected, result)
            self.assertIn("Startup stopped by shutdown signal", logs)
            self.assertIn("workers reaped", logs)
            self.assertNotIn("JAVA_SENTINEL", logs)
            self.assertLess(elapsed, 8)

    def test_hard_deadline_kills_an_unresponsive_generator_and_starts_java(self):
        result, logs, elapsed = self.run_startup("true", stalled_generator=True)
        self.assertEqual(0, result)
        self.assertIn("Stalled generator", logs)
        self.assertIn("failed or timed out; Java will start", logs)
        self.assertIn("JAVA_SENTINEL", logs)
        self.assertLess(elapsed, 20)


if __name__ == "__main__":
    unittest.main()
