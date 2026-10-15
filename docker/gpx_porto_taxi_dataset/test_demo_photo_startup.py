from __future__ import annotations

import hashlib
import os
import random
import signal
import subprocess
import sys
import tempfile
import time
import unittest
import zipfile
from pathlib import Path
from datetime import datetime
from unittest.mock import patch

from demo_track_catalog import build_catalog, load_tracks
from generate_demo_photos import (PROGRESS_FILE_NAME, DEFAULT_WORKERS, MAX_WORKERS, collect_tracks, configured_integer,
                                  generate_demo_photos, render_demo_photo, DemoPhotoRenderTask)
import test_generate_demo_photos as fixtures


def render_out_of_order(task):
    if task.photo_index == 1:
        time.sleep(0.5)
        raise RuntimeError("first worker interrupted")
    return render_demo_photo(task)


class DemoPhotoStartupTest(unittest.TestCase):
    def test_render_failure_preserves_existing_photo_and_cleans_temporary_output(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "demo_photo_00001.jpg"
            output.write_bytes(b"previous complete photo")
            task = DemoPhotoRenderTask(1, 41.15, -8.62, datetime(2026, 1, 1), output, random.Random(1))
            def interrupted_render(_lat, _lon, _time, path, **_kwargs):
                path.write_bytes(b"partial render")
                raise OSError("synthetic codec failure")
            with patch("generate_demo_photos.generate_placeholder_jpeg", side_effect=interrupted_render):
                with self.assertLogs("DemoPhotos", level="ERROR") as logs:
                    with self.assertRaisesRegex(OSError, "synthetic codec failure"):
                        render_demo_photo(task)
            self.assertEqual(b"previous complete photo", output.read_bytes())
            self.assertEqual([], list(output.parent.glob("*.part-*")))
            self.assertIn("index=1", logs.output[0])

    def test_catalog_matches_parser_and_rejects_stale_partial_or_modified_extractions(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            tracks = fixtures.GenerateDemoPhotosTest._write_tracks(root)
            (tracks / "003-invalid.gpx").write_text("invalid XML")
            (tracks / "nested").mkdir()
            original_track = (tracks / "001-first.gpx").read_bytes()
            (tracks / "nested" / "001-copy.gpx").write_bytes(original_track)
            (tracks / ".hidden.gpx").write_bytes(original_track)
            (tracks / ".hidden").mkdir()
            (tracks / ".hidden" / "track.gpx").write_bytes(original_track)
            archive = root / "tracks.zip"
            with zipfile.ZipFile(archive, "w") as output:
                for path in sorted(tracks.rglob("*.gpx"), reverse=True):
                    output.write(path, path.relative_to(tracks).as_posix())
            archive_bytes = archive.read_bytes()
            catalog = root / "catalog.json.gz"
            build_catalog(archive, catalog)
            expected = collect_tracks(tracks)
            self.assertEqual(expected, load_tracks(tracks, catalog, archive))
            self.assertTrue(all(track.start.tzinfo is None for track in expected))
            with patch("generate_demo_photos.collect_tracks", return_value=expected) as fallback:
                load_tracks(tracks, catalog, archive)
                fallback.assert_not_called()
                selected = tracks / "001-first.gpx"
                original = selected.read_bytes()
                selected.write_bytes(original.replace(b"41.15", b"41.16"))
                self.assertEqual(expected, load_tracks(tracks, catalog, archive))
                self.assertEqual(1, fallback.call_count)
                selected.unlink()
                load_tracks(tracks, catalog, archive)
                self.assertEqual(2, fallback.call_count)
                selected.write_bytes(original)
                (tracks / "004-extra.gpx").write_bytes(original)
                load_tracks(tracks, catalog, archive)
                self.assertEqual(3, fallback.call_count)
                (tracks / "004-extra.gpx").unlink()
                archive.write_bytes(b"upgraded archive")
                load_tracks(tracks, catalog, archive)
                self.assertEqual(4, fallback.call_count)
                archive.write_bytes(archive_bytes)
                catalog.write_bytes(b"corrupt catalog")
                load_tracks(tracks, catalog, archive)
                self.assertEqual(5, fallback.call_count)

    def test_parallel_matches_serial_and_resume_repairs_gaps_without_loading_completed_catalog(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            tracks = fixtures.GenerateDemoPhotosTest._write_tracks(root)
            serial, parallel = root / "serial", root / "parallel"
            generate_demo_photos(tracks, serial, 8, 1)
            generate_demo_photos(tracks, parallel, 8, 1, workers=2)
            expected = self.hashes(serial)
            self.assertEqual(expected, self.hashes(parallel))
            for workers in (DEFAULT_WORKERS, MAX_WORKERS):
                media = root / f"workers-{workers}"
                generate_demo_photos(tracks, media, 8, 1, workers=workers)
                self.assertEqual(expected, self.hashes(media))
            with patch("demo_track_catalog.load_tracks", side_effect=AssertionError("must return early")):
                self.assertEqual((0, 0), generate_demo_photos(tracks, parallel, 8, workers=2))
                self.assertEqual((0, 0), generate_demo_photos(tracks, parallel, 0, workers=2))
                generate_demo_photos(tracks, parallel, 4, workers=2)
            output = parallel / "demo-photos"
            (output / "demo_photo_00003.jpg").unlink()
            (output / "demo_photo_00001.jpg.part-123").write_bytes(b"incomplete")
            (output / "other.jpg.part-123").write_bytes(b"unrelated")
            generate_demo_photos(tracks, parallel, 8, 1, workers=2)
            self.assertEqual(expected, self.hashes(parallel))
            self.assertFalse((output / "demo_photo_00001.jpg.part-123").exists())
            self.assertTrue((output / "other.jpg.part-123").exists())
            generate_demo_photos(tracks, parallel, 10, 1, workers=2)
            self.assertEqual("10", (output / PROGRESS_FILE_NAME).read_text().strip())
            self.assertEqual(10, len(self.hashes(parallel)))

    def test_checkpoint_stays_contiguous_when_an_out_of_order_file_exists(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            tracks = fixtures.GenerateDemoPhotosTest._write_tracks(root)
            output = root / "media" / "demo-photos"
            output.mkdir(parents=True)
            (output / PROGRESS_FILE_NAME).write_text("3")
            (output / "demo_photo_00003.jpg").write_bytes(b"already completed")
            def interrupted(task):
                if task.photo_index == 2:
                    raise RuntimeError("interrupted")
                task.output_path.write_bytes(b"finished")
                return task.photo_index
            with patch("generate_demo_photos.render_demo_photo", side_effect=interrupted):
                with self.assertRaisesRegex(RuntimeError, "interrupted"):
                    generate_demo_photos(tracks, root / "media", 4)
            self.assertEqual("1", (output / PROGRESS_FILE_NAME).read_text().strip())
            generate_demo_photos(tracks, root / "media", 4)
            self.assertEqual("4", (output / PROGRESS_FILE_NAME).read_text().strip())

    def test_worker_finishing_out_of_order_does_not_advance_checkpoint_past_a_gap(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            tracks = fixtures.GenerateDemoPhotosTest._write_tracks(root)
            media = root / "media"
            with patch("generate_demo_photos.render_demo_photo", render_out_of_order):
                with self.assertRaisesRegex(RuntimeError, "first worker interrupted"):
                    generate_demo_photos(tracks, media, 4, workers=2)
            output = media / "demo-photos"
            self.assertTrue((output / "demo_photo_00002.jpg").exists())
            self.assertFalse((output / "demo_photo_00001.jpg").exists())
            self.assertEqual("0", (output / PROGRESS_FILE_NAME).read_text().strip())
            generate_demo_photos(tracks, media, 4, workers=2)
            self.assertEqual("4", (output / PROGRESS_FILE_NAME).read_text().strip())

    def test_invalid_configuration_uses_defaults_or_caps(self):
        self.assertEqual(4, configured_integer("workers", "bad", DEFAULT_WORKERS, maximum=MAX_WORKERS))
        self.assertEqual(4, configured_integer("workers", "0", DEFAULT_WORKERS, maximum=MAX_WORKERS))
        self.assertEqual(6, configured_integer("workers", "6", DEFAULT_WORKERS, maximum=MAX_WORKERS))
        self.assertEqual(6, configured_integer("workers", "20", DEFAULT_WORKERS, maximum=MAX_WORKERS))
        self.assertEqual(0, configured_integer("photos", "0", 200, minimum=0))
        self.assertEqual(200, configured_integer("photos", "-1", 200, minimum=0))
        self.assertEqual(600, configured_integer("deadline", "1000", 0, maximum=600))

    def test_cli_configuration_falls_back_to_calculated_deadline_or_caps_explicit_values(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            tracks = fixtures.GenerateDemoPhotosTest._write_tracks(root)
            script = Path(__file__).with_name("generate_demo_photos.py")
            for count, workers, deadline, expected in (
                ("bad", "0", "bad", "workers=4, deadline=110s"),
                ("8", "99", "9999", "workers=6, deadline=600s"),
                ("8", None, "", "workers=4, deadline=62s"),
            ):
                environment = dict(os.environ, DEMO_PHOTO_GENERATION_TIMEOUT_SECONDS=deadline)
                if workers is None:
                    environment.pop("DEMO_PHOTO_WORKERS", None)
                else:
                    environment["DEMO_PHOTO_WORKERS"] = workers
                media = root / f"{count}-{workers}"
                result = subprocess.run([sys.executable, str(script), str(tracks), str(media), count],
                                        env=environment,
                                        capture_output=True, text=True, timeout=12)
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertIn(expected, result.stdout)
                if workers is not None:
                    self.assertIn("WARNING", result.stdout)

    def test_deadline_and_signals_reap_workers_and_resume_a_large_count(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            tracks = fixtures.GenerateDemoPhotosTest._write_tracks(root)
            script = Path(__file__).with_name("generate_demo_photos.py")
            for signum in (None, signal.SIGTERM, signal.SIGINT):
                media = root / str(signum)
                environment = dict(os.environ, DEMO_PHOTO_WORKERS="2",
                                   DEMO_PHOTO_GENERATION_TIMEOUT_SECONDS="1" if signum is None else "600")
                with (root / f"{signum}.log").open("w+") as log:
                    process = subprocess.Popen([sys.executable, "-u", str(script), str(tracks),
                                                str(media), "20000", "1"],
                                               env=environment, stdout=log, stderr=log)
                    if signum is not None:
                        limit = time.monotonic() + 5
                        while not list(media.glob("demo-photos/*.jpg")) and time.monotonic() < limit:
                            time.sleep(0.02)
                        process.send_signal(signum)
                    result = process.wait(timeout=8)
                    self.assertEqual(1 if signum is None else 128 + signum, result)
                    log.seek(0)
                    self.assertNotIn("leaked semaphore", log.read())
                output = media / "demo-photos"
                checkpoint = int((output / PROGRESS_FILE_NAME).read_text())
                self.assertLess(checkpoint, 20000)
                self.assertTrue(all((output / f"demo_photo_{index:05d}.jpg").exists()
                                    for index in range(1, checkpoint + 1)))
                # A restart grows the contiguous prefix after the same partial run.
                subprocess.run([sys.executable, str(script), str(tracks), str(media), "20000", "1"],
                               env=dict(environment, DEMO_PHOTO_GENERATION_TIMEOUT_SECONDS="1"),
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=8)
                self.assertGreater(int((output / PROGRESS_FILE_NAME).read_text()), checkpoint)

    @staticmethod
    def hashes(media):
        return {path.name: hashlib.sha256(path.read_bytes()).hexdigest()
                for path in (media / "demo-photos").glob("*.jpg")}


if __name__ == "__main__":
    unittest.main()
