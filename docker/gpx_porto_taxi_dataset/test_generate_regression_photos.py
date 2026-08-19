from __future__ import annotations

import hashlib
import json
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

import piexif

from generate_regression_photos import (
    AUDIO_CODEC,
    FIXTURES,
    TRACK_POINTS,
    VIDEO_CODEC,
    VIDEO_DURATION_SECONDS,
    VIDEO_FIXTURES,
    generate_regression_fixtures,
    iso6709,
)


class GenerateRegressionPhotosTest(unittest.TestCase):

    def test_generates_four_gps_timed_and_two_camera_timed_photos(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            output_directory = Path(temporary_directory)
            manifest_path = generate_regression_fixtures(output_directory)
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))

            expected_by_name = {fixture.name: fixture for fixture in FIXTURES}
            self.assertEqual(6, len(manifest["fixtures"]))
            self.assertEqual(
                {"EXIF_GPS": 4, "EXIF_DATE_TAKEN": 2},
                self._counts(row["expected_time_source"] for row in manifest["fixtures"]),
            )

            track_start = datetime.fromisoformat(TRACK_POINTS[0][2].replace("Z", "+00:00"))
            track_end = datetime.fromisoformat(TRACK_POINTS[-1][2].replace("Z", "+00:00"))
            camera_captures = (
                datetime.strptime(fixture.captured_at, "%Y:%m:%d %H:%M:%S").replace(tzinfo=timezone.utc)
                for fixture in FIXTURES
                if fixture.expected_time_source == "EXIF_DATE_TAKEN"
            )
            for camera_capture in camera_captures:
                self.assertLessEqual(track_start, camera_capture)
                self.assertLessEqual(camera_capture + timedelta(minutes=15), track_end)

            for row in manifest["fixtures"]:
                fixture = expected_by_name[row["name"]]
                exif = piexif.load(str(output_directory / fixture.name))
                gps = exif["GPS"]
                captured_at = datetime.strptime(fixture.captured_at, "%Y:%m:%d %H:%M:%S")

                self.assertEqual(
                    fixture.captured_at.encode(),
                    exif["Exif"][piexif.ExifIFD.DateTimeOriginal],
                )
                if fixture.has_gps:
                    self.assertEqual(
                        captured_at.strftime("%Y:%m:%d").encode(),
                        gps[piexif.GPSIFD.GPSDateStamp],
                    )
                    gps_time = gps[piexif.GPSIFD.GPSTimeStamp]
                    self.assertEqual(
                        (captured_at.hour, captured_at.minute, captured_at.second),
                        tuple(round(numerator / denominator) for numerator, denominator in gps_time),
                    )
                    self.assertIn(piexif.GPSIFD.GPSLatitude, gps)
                    self.assertIn(piexif.GPSIFD.GPSLongitude, gps)
                else:
                    self.assertNotIn(piexif.GPSIFD.GPSDateStamp, gps)
                    self.assertNotIn(piexif.GPSIFD.GPSTimeStamp, gps)
                    self.assertNotIn(piexif.GPSIFD.GPSLatitude, gps)
                    self.assertNotIn(piexif.GPSIFD.GPSLongitude, gps)

    def test_generates_mp4_and_mov_video_matrix(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            output_directory = Path(temporary_directory)
            manifest_path = generate_regression_fixtures(output_directory)
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))

            expected_by_name = {fixture.name: fixture for fixture in VIDEO_FIXTURES}
            self.assertEqual({"mp4", "mov"}, {row["container"] for row in manifest["videos"]})
            self.assertEqual(2, len(manifest["videos"]))

            estimated_video = next(
                fixture for fixture in VIDEO_FIXTURES
                if fixture.expected_origin == "TRACK_INTERPOLATED"
            )
            capture_time = datetime.fromisoformat(estimated_video.captured_at.replace("Z", "+00:00"))
            before = TRACK_POINTS[2]
            after = TRACK_POINTS[3]
            before_time = datetime.fromisoformat(before[2].replace("Z", "+00:00"))
            after_time = datetime.fromisoformat(after[2].replace("Z", "+00:00"))
            fraction = (capture_time - before_time) / (after_time - before_time)
            self.assertAlmostEqual(
                before[0] + fraction * (after[0] - before[0]),
                estimated_video.expected_latitude,
            )
            self.assertAlmostEqual(
                before[1] + fraction * (after[1] - before[1]),
                estimated_video.expected_longitude,
            )

            for row in manifest["videos"]:
                fixture = expected_by_name[row["name"]]
                content = (output_directory / fixture.name).read_bytes()
                streams = row["ffprobe"]["streams"]
                video_stream = next(stream for stream in streams if stream["codec_type"] == "video")
                audio_stream = next(stream for stream in streams if stream["codec_type"] == "audio")

                self.assertEqual(hashlib.sha256(content).hexdigest(), row["sha256"])
                self.assertEqual(VIDEO_CODEC, video_stream["codec_name"])
                self.assertEqual(AUDIO_CODEC, audio_stream["codec_name"])
                self.assertAlmostEqual(
                    VIDEO_DURATION_SECONDS,
                    float(row["ffprobe"]["format"]["duration"]),
                    delta=0.1,
                )
                self.assertEqual(fixture.expected_origin, row["expected_origin"])
                if fixture.has_embedded_gps:
                    tags = row["ffprobe"]["format"]["tags"]
                    self.assertIn("location", tags)
                    self.assertEqual(
                        iso6709(fixture.expected_latitude, fixture.expected_longitude),
                        tags["location"],
                    )
                else:
                    self.assertNotIn("location", row["ffprobe"]["format"].get("tags", {}))

    @staticmethod
    def _counts(values) -> dict[str, int]:
        counts: dict[str, int] = {}
        for value in values:
            counts[value] = counts.get(value, 0) + 1
        return counts


if __name__ == "__main__":
    unittest.main()
