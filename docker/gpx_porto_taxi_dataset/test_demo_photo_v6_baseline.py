"""Run in the app image to retain its Pillow build and DejaVu fonts."""
import hashlib
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

from demo_track_catalog import build_catalog, load_tracks, sha256
from generate_demo_photos import (collect_tracks, prepare_render_task, render_demo_photo,
                                  standalone_capture_gaps)


class DemoPhotoV6BaselineTest(unittest.TestCase):
    def test_packaged_catalog_equals_parser_and_selected_photos_match_frozen_v6(self):
        source = Path(__file__).parent
        archive = source / "porto_taxi_service_gpx_extract.zip"
        expected = json.loads((source / "demo-photo-v6-baseline.json").read_text())
        self.assertEqual(expected["zip_sha256"], sha256(archive))
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            tracks_dir = root / "tracks"
            with zipfile.ZipFile(archive) as zipped:
                zipped.extractall(tracks_dir)
            catalog = root / "catalog.json.gz"
            build_catalog(archive, catalog)
            tracks = load_tracks(tracks_dir, catalog, archive)
            self.assertEqual(collect_tracks(tracks_dir), tracks)
            eligible = [track for track in tracks[:expected["eligible_track_count"]]
                        if track.timed_segment_count > 0]
            positions = [track for track in tracks if track.timed_segment_count > 0]
            gaps = standalone_capture_gaps(tracks)
            latest = max(track.end for track in tracks)
            for index, digest in expected["photos"].items():
                task, _ = prepare_render_task(int(index), root, eligible, positions, gaps, latest)
                render_demo_photo(task)
                self.assertEqual(digest, hashlib.sha256(task.output_path.read_bytes()).hexdigest(), index)
