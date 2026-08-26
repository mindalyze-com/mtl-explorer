from __future__ import annotations

import hashlib
import math
import tempfile
import unittest
from datetime import datetime, timedelta
from pathlib import Path
from unittest.mock import patch

import piexif
from PIL import Image

from generate_demo_photos import (
    DEMO_PHOTO_FORMAT_VERSION,
    DEMO_PHOTO_HEIGHT,
    DEMO_PHOTO_WIDTH,
    EARTH_RADIUS_M,
    MAX_OFFSET_METERS,
    PROGRESS_FILE_NAME,
    collect_tracks,
    generate_demo_photos,
    is_track_linked_photo,
)
from photo_placeholder import PALETTES, _gradient_strip


FIRST_TRACK_START = datetime(2026, 1, 1, 10, 0, 0)
FIRST_TRACK_END = datetime(2026, 1, 1, 10, 10, 0)
SECOND_TRACK_START = datetime(2026, 1, 2, 12, 0, 0)
SECOND_TRACK_END = datetime(2026, 1, 2, 12, 10, 0)
FIRST_TRACK_LATITUDE = 41.15
FIRST_TRACK_LONGITUDE = -8.62
SECOND_TRACK_LATITUDE = 41.19
SECOND_TRACK_LONGITUDE = -8.58


class GenerateDemoPhotosTest(unittest.TestCase):

    def test_generates_linked_and_standalone_photos_with_complete_exif(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            tracks_directory = self._write_tracks(root)

            generated = generate_demo_photos(tracks_directory, root / "media", 6, eligible_track_count=1)

            self.assertEqual((3, 3), generated)
            output_directory = root / "media" / "demo-photos"
            photos = sorted(output_directory.glob("demo_photo_*.jpg"))
            self.assertEqual(6, len(photos))
            self.assertEqual([], list(output_directory.glob("*.part-*")))

            all_track_windows = (
                (FIRST_TRACK_START, FIRST_TRACK_END),
                (SECOND_TRACK_START, SECOND_TRACK_END),
            )
            for photo_index, photo in enumerate(photos, start=1):
                with Image.open(photo) as image:
                    self.assertEqual("JPEG", image.format)
                    self.assertEqual((DEMO_PHOTO_WIDTH, DEMO_PHOTO_HEIGHT), image.size)

                exif = piexif.load(str(photo))
                gps = exif["GPS"]
                captured_at = self._gps_capture_time(gps)
                self.assertEqual(
                    captured_at.strftime("%Y:%m:%d %H:%M:%S").encode(),
                    exif["Exif"][piexif.ExifIFD.DateTimeOriginal],
                )

                latitude = self._gps_coordinate(
                    gps[piexif.GPSIFD.GPSLatitude],
                    gps[piexif.GPSIFD.GPSLatitudeRef],
                )
                longitude = self._gps_coordinate(
                    gps[piexif.GPSIFD.GPSLongitude],
                    gps[piexif.GPSIFD.GPSLongitudeRef],
                )

                if is_track_linked_photo(photo_index):
                    self.assertLessEqual(FIRST_TRACK_START, captured_at)
                    self.assertLessEqual(captured_at, FIRST_TRACK_END)
                    self.assertLessEqual(
                        self._haversine_meters(
                            FIRST_TRACK_LATITUDE,
                            FIRST_TRACK_LONGITUDE,
                            latitude,
                            longitude,
                        ),
                        MAX_OFFSET_METERS + 0.1,
                    )
                else:
                    self.assertFalse(
                        any(start <= captured_at <= end for start, end in all_track_windows)
                    )
                    self.assertLessEqual(
                        min(
                            self._haversine_meters(
                                FIRST_TRACK_LATITUDE,
                                FIRST_TRACK_LONGITUDE,
                                latitude,
                                longitude,
                            ),
                            self._haversine_meters(
                                SECOND_TRACK_LATITUDE,
                                SECOND_TRACK_LONGITUDE,
                                latitude,
                                longitude,
                            ),
                        ),
                        0.1,
                    )

    def test_generation_is_deterministic(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            tracks_directory = self._write_tracks(root)
            first_media = root / "first-media"
            second_media = root / "second-media"

            generate_demo_photos(tracks_directory, first_media, 4, eligible_track_count=1)
            generate_demo_photos(tracks_directory, second_media, 4, eligible_track_count=1)

            first_hashes = self._photo_hashes(first_media / "demo-photos")
            second_hashes = self._photo_hashes(second_media / "demo-photos")
            self.assertEqual(first_hashes, second_hashes)

    def test_track_catalog_retains_metadata_instead_of_timed_segments(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            tracks_directory = self._write_tracks(root)

            tracks = collect_tracks(tracks_directory)

            self.assertEqual(2, len(tracks))
            self.assertEqual([1, 1], [track.timed_segment_count for track in tracks])
            self.assertEqual(
                ["001-first.gpx", "002-second.gpx"],
                [track.path.name for track in tracks],
            )
            self.assertTrue(all(not hasattr(track, "timed_segments") for track in tracks))

    def test_progress_resumes_after_the_last_atomically_published_photo(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            tracks_directory = self._write_tracks(root)
            media_directory = root / "media"
            attempted: list[int] = []

            def fail_on_third_photo(task) -> int:
                attempted.append(task.photo_index)
                if task.photo_index == 3:
                    raise RuntimeError("synthetic interruption")
                task.output_path.write_bytes(f"photo-{task.photo_index}".encode())
                return task.photo_index

            with patch(
                "generate_demo_photos.render_demo_photo",
                side_effect=fail_on_third_photo,
            ):
                with self.assertRaisesRegex(RuntimeError, "synthetic interruption"):
                    generate_demo_photos(
                        tracks_directory,
                        media_directory,
                        4,
                        eligible_track_count=1,
                    )

            progress_path = media_directory / "demo-photos" / PROGRESS_FILE_NAME
            self.assertEqual([1, 2, 3], attempted)
            self.assertEqual("2", progress_path.read_text(encoding="utf-8").strip())

            resumed: list[int] = []

            def record_resumed_photo(task) -> int:
                resumed.append(task.photo_index)
                task.output_path.write_bytes(f"photo-{task.photo_index}".encode())
                return task.photo_index

            with patch(
                "generate_demo_photos.render_demo_photo",
                side_effect=record_resumed_photo,
            ):
                generate_demo_photos(
                    tracks_directory,
                    media_directory,
                    4,
                    eligible_track_count=1,
                )

            self.assertEqual([3, 4], resumed)
            self.assertEqual("4", progress_path.read_text(encoding="utf-8").strip())

    def test_gradient_cache_keeps_only_compact_vertical_strips(self) -> None:
        _gradient_strip.cache_clear()

        strips = [
            _gradient_strip(dark, light, DEMO_PHOTO_HEIGHT)
            for dark, light in PALETTES
        ]

        self.assertTrue(all(strip.size == (1, DEMO_PHOTO_HEIGHT) for strip in strips))
        self.assertEqual(len(PALETTES), _gradient_strip.cache_info().currsize)

    def test_new_format_replaces_legacy_generated_photo(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            tracks_directory = self._write_tracks(root)
            output_directory = root / "media" / "demo-photos"
            output_directory.mkdir(parents=True)
            legacy_photo = output_directory / "demo_photo_00001.jpg"
            legacy_photo.write_bytes(b"legacy-small-photo")
            (output_directory / ".demo-photo-format-v2.progress").write_text(
                "1\n", encoding="utf-8"
            )

            generate_demo_photos(tracks_directory, root / "media", 1, eligible_track_count=1)

            with Image.open(legacy_photo) as image:
                self.assertEqual((DEMO_PHOTO_WIDTH, DEMO_PHOTO_HEIGHT), image.size)
            self.assertEqual(
                "1",
                (output_directory / PROGRESS_FILE_NAME).read_text(encoding="utf-8").strip(),
            )
            self.assertEqual(6, DEMO_PHOTO_FORMAT_VERSION)

    @staticmethod
    def _write_tracks(root: Path) -> Path:
        tracks_directory = root / "tracks"
        tracks_directory.mkdir()
        GenerateDemoPhotosTest._write_track(
            tracks_directory / "001-first.gpx",
            FIRST_TRACK_LATITUDE,
            FIRST_TRACK_LONGITUDE,
            FIRST_TRACK_START,
            FIRST_TRACK_END,
        )
        GenerateDemoPhotosTest._write_track(
            tracks_directory / "002-second.gpx",
            SECOND_TRACK_LATITUDE,
            SECOND_TRACK_LONGITUDE,
            SECOND_TRACK_START,
            SECOND_TRACK_END,
        )
        return tracks_directory

    @staticmethod
    def _write_track(
        path: Path,
        latitude: float,
        longitude: float,
        start: datetime,
        end: datetime,
    ) -> None:
        path.write_text(
            f'''<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="MTL Explorer synthetic demo test"
     xmlns="http://www.topografix.com/GPX/1/1">
  <trk><trkseg>
    <trkpt lat="{latitude}" lon="{longitude}"><time>{start:%Y-%m-%dT%H:%M:%S}Z</time></trkpt>
    <trkpt lat="{latitude}" lon="{longitude}"><time>{end:%Y-%m-%dT%H:%M:%S}Z</time></trkpt>
  </trkseg></trk>
</gpx>
''',
            encoding="utf-8",
        )

    @staticmethod
    def _gps_capture_time(gps: dict[int, object]) -> datetime:
        date_value = gps[piexif.GPSIFD.GPSDateStamp].decode()
        hours, minutes, seconds = (
            numerator / denominator
            for numerator, denominator in gps[piexif.GPSIFD.GPSTimeStamp]
        )
        date = datetime.strptime(date_value, "%Y:%m:%d")
        return date + timedelta(hours=hours, minutes=minutes, seconds=seconds)

    @staticmethod
    def _gps_coordinate(values: tuple[tuple[int, int], ...], reference: bytes) -> float:
        degrees, minutes, seconds = (
            numerator / denominator for numerator, denominator in values
        )
        coordinate = degrees + minutes / 60 + seconds / 3600
        return -coordinate if reference in (b"S", b"W") else coordinate

    @staticmethod
    def _haversine_meters(
        latitude_a: float,
        longitude_a: float,
        latitude_b: float,
        longitude_b: float,
    ) -> float:
        latitude_delta = math.radians(latitude_b - latitude_a)
        longitude_delta = math.radians(longitude_b - longitude_a)
        latitude_a_radians = math.radians(latitude_a)
        latitude_b_radians = math.radians(latitude_b)
        haversine = (
            math.sin(latitude_delta / 2) ** 2
            + math.cos(latitude_a_radians)
            * math.cos(latitude_b_radians)
            * math.sin(longitude_delta / 2) ** 2
        )
        return EARTH_RADIUS_M * 2 * math.atan2(
            math.sqrt(haversine), math.sqrt(1 - haversine)
        )

    @staticmethod
    def _photo_hashes(directory: Path) -> list[str]:
        return [
            hashlib.sha256(path.read_bytes()).hexdigest()
            for path in sorted(directory.glob("demo_photo_*.jpg"))
        ]


if __name__ == "__main__":
    unittest.main()
