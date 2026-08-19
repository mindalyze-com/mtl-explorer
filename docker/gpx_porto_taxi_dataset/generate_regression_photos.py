#!/usr/bin/env python3
"""Create the disposable media and activity set used by full regression."""

from __future__ import annotations

import argparse
import hashlib
import json
import random
import shutil
import struct
import subprocess
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path

from photo_placeholder import PALETTES, generate_placeholder_jpeg


@dataclass(frozen=True)
class PhotoFixture:
    name: str
    label: str
    latitude: float
    longitude: float
    captured_at: str
    has_gps: bool
    expected_origin: str
    expected_time_source: str


@dataclass(frozen=True)
class VideoFixture:
    name: str
    label: str
    container: str
    captured_at: str
    expected_latitude: float
    expected_longitude: float
    has_embedded_gps: bool
    expected_origin: str
    expected_time_source: str
    playback_expectation: str


REGRESSION_TRACK_NAME = "MTL Regression Media Track"
REGRESSION_TRACK_FILE = "mtl-regression-media-track.gpx"
VIDEO_DURATION_SECONDS = 2
VIDEO_WIDTH = 320
VIDEO_HEIGHT = 180
VIDEO_FRAME_RATE = 24
VIDEO_CODEC = "h264"
AUDIO_CODEC = "aac"
TRACK_POINTS = (
    (46.94780, 7.44710, "2026-08-17T07:59:00Z"),
    (46.94805, 7.44745, "2026-08-17T08:00:00Z"),
    (46.94820, 7.44770, "2026-08-17T08:06:00Z"),
    (46.94845, 7.44800, "2026-08-17T08:12:00Z"),
    (46.94875, 7.44835, "2026-08-17T08:18:00Z"),
    (46.94910, 7.44870, "2026-08-17T08:24:00Z"),
)


FIXTURES = (
    PhotoFixture(
        "mtl-regression-photo-a.jpg",
        "MTL Regression A",
        46.94800,
        7.44740,
        "2026:08:17 08:00:00",
        True,
        "EXIF_EMBEDDED",
        "EXIF_GPS",
    ),
    PhotoFixture(
        "mtl-regression-photo-b.jpg",
        "MTL Regression B",
        46.94818,
        7.44768,
        "2026:08:17 08:01:00",
        True,
        "EXIF_EMBEDDED",
        "EXIF_GPS",
    ),
    PhotoFixture(
        "mtl-regression-estimated-a.jpg",
        "MTL Estimated A",
        46.94833,
        7.44785,
        "2026:08:17 08:01:30",
        False,
        "TRACK_INTERPOLATED",
        "EXIF_DATE_TAKEN",
    ),
    PhotoFixture(
        "mtl-regression-estimated-b.jpg",
        "MTL Estimated B",
        46.94860,
        7.44818,
        "2026:08:17 08:02:30",
        False,
        "TRACK_INTERPOLATED",
        "EXIF_DATE_TAKEN",
    ),
    PhotoFixture(
        "mtl-regression-delete-a.jpg",
        "MTL Delete A",
        46.94836,
        7.44796,
        "2026:08:17 08:02:00",
        True,
        "EXIF_EMBEDDED",
        "EXIF_GPS",
    ),
    PhotoFixture(
        "mtl-regression-delete-b.jpg",
        "MTL Delete B",
        46.94885,
        7.44855,
        "2026:08:17 08:03:00",
        True,
        "EXIF_EMBEDDED",
        "EXIF_GPS",
    ),
)

VIDEO_FIXTURES = (
    VideoFixture(
        "mtl-regression-video-gps.mp4",
        "MTL Regression MP4 GPS",
        "mp4",
        "2026-08-17T08:15:00Z",
        46.94860,
        7.44815,
        True,
        "EXIF_EMBEDDED",
        "EXIF_DATE_TAKEN",
        "REQUIRED_NATIVE_PLAYBACK",
    ),
    VideoFixture(
        "mtl-regression-video-estimated.mov",
        "MTL Regression MOV Estimated",
        "mov",
        "2026-08-17T08:09:00Z",
        46.948325,
        7.44785,
        False,
        "TRACK_INTERPOLATED",
        "EXIF_DATE_TAKEN",
        "BROWSER_CAPABILITY_DEPENDENT",
    ),
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output_dir", type=Path, help="Directory for generated fixtures and manifest")
    return parser.parse_args()


def regression_track_gpx() -> str:
    track_points = "\n".join(
        f'      <trkpt lat="{latitude:.5f}" lon="{longitude:.5f}">'
        f"<ele>{550 + index}</ele><time>{captured_at}</time></trkpt>"
        for index, (latitude, longitude, captured_at) in enumerate(TRACK_POINTS)
    )
    return f'''<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="MTL Explorer regression fixture"
     xmlns="http://www.topografix.com/GPX/1/1"
     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
     xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">
  <metadata><name>{REGRESSION_TRACK_NAME}</name></metadata>
  <trk>
    <name>{REGRESSION_TRACK_NAME}</name>
    <type>walking</type>
    <trkseg>
{track_points}
    </trkseg>
  </trk>
</gpx>
'''


def iso6709(latitude: float, longitude: float) -> str:
    return f"{latitude:+09.5f}{longitude:+010.5f}/"


def top_level_iso_boxes(content: bytes):
    offset = 0
    while offset + 8 <= len(content):
        size = struct.unpack_from(">I", content, offset)[0]
        box_type = content[offset + 4:offset + 8]
        if size == 0:
            size = len(content) - offset
        if size == 1 or size < 8 or offset + size > len(content):
            raise RuntimeError(f"Unsupported ISO media box at byte {offset}")
        yield offset, size, box_type
        offset += size


def inject_mp4_gps(destination: Path, latitude: float, longitude: float) -> None:
    content = destination.read_bytes()
    boxes = list(top_level_iso_boxes(content))
    movie_box = next((box for box in boxes if box[2] == b"moov"), None)
    media_box = next((box for box in boxes if box[2] == b"mdat"), None)
    if movie_box is None or media_box is None:
        raise RuntimeError(f"Missing moov or mdat box in {destination.name}")

    movie_offset, movie_size, _ = movie_box
    media_offset, media_size, _ = media_box
    if movie_offset < media_offset + media_size:
        raise RuntimeError(f"Expected moov after mdat before adding GPS to {destination.name}")

    location = iso6709(latitude, longitude).encode("utf-8")
    location_atom = struct.pack(">I4sHH", 12 + len(location), b"\xa9xyz", len(location), 0) + location
    user_data_atom = struct.pack(">I4s", 8 + len(location_atom), b"udta") + location_atom
    updated_movie_size = movie_size + len(user_data_atom)
    updated_movie = (
        struct.pack(">I", updated_movie_size)
        + content[movie_offset + 4:movie_offset + movie_size]
        + user_data_atom
    )
    destination.write_bytes(
        content[:movie_offset] + updated_movie + content[movie_offset + movie_size:]
    )


def require_media_tool(name: str) -> str:
    executable = shutil.which(name)
    if executable is None:
        raise RuntimeError(f"{name} is required to generate regression videos")
    return executable


def generate_video_fixture(fixture: VideoFixture, destination: Path, index: int) -> dict:
    ffmpeg = require_media_tool("ffmpeg")
    tone_frequency = 660 + index * 220
    command = [
        ffmpeg,
        "-hide_banner",
        "-loglevel", "error",
        "-y",
        "-f", "lavfi",
        "-i", f"testsrc2=size={VIDEO_WIDTH}x{VIDEO_HEIGHT}:rate={VIDEO_FRAME_RATE}",
        "-f", "lavfi",
        "-i", f"sine=frequency={tone_frequency}:sample_rate=48000",
        "-t", str(VIDEO_DURATION_SECONDS),
        "-map", "0:v:0",
        "-map", "1:a:0",
        "-c:v", "libx264",
        "-preset", "ultrafast",
        "-pix_fmt", "yuv420p",
        "-c:a", "aac",
        "-b:a", "64k",
        "-metadata", f"title={fixture.label}",
        "-metadata", f"creation_time={fixture.captured_at}",
    ]
    if fixture.has_embedded_gps:
        location = iso6709(fixture.expected_latitude, fixture.expected_longitude)
        command.extend(["-metadata", f"location={location}"])
    if fixture.container == "mov":
        command.extend(["-movflags", "+faststart"])
    command.append(str(destination))

    completed = subprocess.run(command, capture_output=True, text=True, check=False)
    if completed.returncode != 0:
        raise RuntimeError(f"ffmpeg failed for {fixture.name}: {completed.stderr.strip()}")
    if fixture.has_embedded_gps:
        inject_mp4_gps(destination, fixture.expected_latitude, fixture.expected_longitude)

    ffprobe = require_media_tool("ffprobe")
    probe = subprocess.run(
        [
            ffprobe,
            "-v", "error",
            "-show_entries",
            "format=format_name,duration:format_tags=creation_time,location:"
            "stream=codec_type,codec_name,width,height",
            "-of", "json",
            str(destination),
        ],
        capture_output=True,
        text=True,
        check=False,
    )
    if probe.returncode != 0:
        raise RuntimeError(f"ffprobe failed for {fixture.name}: {probe.stderr.strip()}")
    return json.loads(probe.stdout)


def generate_regression_fixtures(output_dir: Path) -> Path:
    output_dir.mkdir(parents=True, exist_ok=True)
    manifest = {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "generator": Path(__file__).name,
        "renderer": "photo_placeholder.py",
        "fixtures": [],
        "videos": [],
    }

    track_path = output_dir / REGRESSION_TRACK_FILE
    track_path.write_text(regression_track_gpx(), encoding="utf-8")
    track_content = track_path.read_bytes()
    manifest["activity"] = {
        "name": REGRESSION_TRACK_NAME,
        "file": REGRESSION_TRACK_FILE,
        "bytes": len(track_content),
        "sha256": hashlib.sha256(track_content).hexdigest(),
        "trackpoint_count": len(TRACK_POINTS),
        "timestamp_count": len(TRACK_POINTS),
    }

    for index, fixture in enumerate(FIXTURES):
        destination = output_dir / fixture.name
        captured_at = datetime.strptime(fixture.captured_at, "%Y:%m:%d %H:%M:%S")
        generate_placeholder_jpeg(
            fixture.latitude,
            fixture.longitude,
            captured_at,
            destination,
            title=fixture.label,
            palette=PALETTES[index],
            rng=random.Random(fixture.name),
            include_gps=fixture.has_gps,
            include_gps_timestamp=fixture.has_gps,
        )
        content = destination.read_bytes()
        manifest["fixtures"].append(
            {
                **asdict(fixture),
                "bytes": len(content),
                "sha256": hashlib.sha256(content).hexdigest(),
            }
        )

    for index, fixture in enumerate(VIDEO_FIXTURES):
        destination = output_dir / fixture.name
        probe = generate_video_fixture(fixture, destination, index)
        content = destination.read_bytes()
        manifest["videos"].append(
            {
                **asdict(fixture),
                "expected_video_codec": VIDEO_CODEC,
                "expected_audio_codec": AUDIO_CODEC,
                "expected_duration_seconds": VIDEO_DURATION_SECONDS,
                "bytes": len(content),
                "sha256": hashlib.sha256(content).hexdigest(),
                "ffprobe": probe,
            }
        )

    manifest_path = output_dir / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    return manifest_path


def main() -> None:
    args = parse_args()
    manifest_path = generate_regression_fixtures(args.output_dir)
    print(manifest_path)


if __name__ == "__main__":
    main()
