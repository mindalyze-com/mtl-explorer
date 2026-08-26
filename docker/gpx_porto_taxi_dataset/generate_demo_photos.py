#!/usr/bin/env python3
"""
generate_demo_photos.py — Create geotagged placeholder JPEG photos for demo mode.

Usage:
    python3 generate_demo_photos.py \
        <gpx_dir> <media_output_dir> <num_photos> [eligible_track_count]

- Scans <gpx_dir> for *.gpx files.
- Generates a deterministic split of track-linked and standalone Porto photos.
- Track-linked photos have capture times inside an eligible GPX track and are
  positioned no more than 25 m from its interpolated route position.
- Standalone photos use route-derived land positions and capture times that do
  not overlap any GPX track.
- Writes 1920x1440 JPEGs with GPS coordinates, DateTimeOriginal, and GPS time to
  <media_output_dir>/demo-photos/.
- A versioned progress marker limits restart work and upgrades legacy photos.

Dependencies (available in the Docker image):
    pip install Pillow piexif
"""

from __future__ import annotations

import glob
import math
import os
import random
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from datetime import datetime, timedelta
from pathlib import Path

from photo_placeholder import generate_placeholder_jpeg


MAX_OFFSET_METERS = 25
EARTH_RADIUS_M = 6_371_000
DEMO_PHOTO_WIDTH = 1920
DEMO_PHOTO_HEIGHT = 1440
DEMO_PHOTO_JPEG_QUALITY = 82
DEMO_PHOTO_FORMAT_VERSION = 6
PROGRESS_FILE_NAME = f".demo-photo-format-v{DEMO_PHOTO_FORMAT_VERSION}.progress"
LINKED_PHOTO_REMAINDER = 1
LINKED_PHOTO_MODULUS = 2
MIN_SEGMENT_RATIO = 0.1
MAX_SEGMENT_RATIO = 0.9
ONE_SECOND = timedelta(seconds=1)
STANDALONE_FALLBACK_OFFSET = timedelta(days=1)
PHOTO_GENERATION_PROGRESS_INTERVAL = 50


@dataclass(frozen=True, slots=True)
class TrackPoint:
    latitude: float
    longitude: float
    captured_at: datetime


@dataclass(frozen=True, slots=True)
class DemoTrack:
    path: Path
    timed_segment_count: int
    start: datetime
    end: datetime


@dataclass(frozen=True, slots=True)
class CaptureGap:
    start: datetime
    seconds: int


@dataclass(frozen=True, slots=True)
class DemoPhotoRenderTask:
    photo_index: int
    latitude: float
    longitude: float
    captured_at: datetime
    output_path: Path
    rng: random.Random


def render_demo_photo(task: DemoPhotoRenderTask) -> int:
    temporary_path = task.output_path.with_suffix(
        f"{task.output_path.suffix}.part-{os.getpid()}"
    )
    temporary_path.unlink(missing_ok=True)
    try:
        generate_placeholder_jpeg(
            task.latitude,
            task.longitude,
            task.captured_at,
            temporary_path,
            title=f"Demo photo {task.photo_index:05d}",
            rng=task.rng,
            include_gps_timestamp=True,
            width=DEMO_PHOTO_WIDTH,
            height=DEMO_PHOTO_HEIGHT,
            jpeg_quality=DEMO_PHOTO_JPEG_QUALITY,
        )
        temporary_path.replace(task.output_path)
    finally:
        temporary_path.unlink(missing_ok=True)
    return task.photo_index


def gpx_namespace(root: ET.Element) -> str:
    if root.tag.startswith("{"):
        return root.tag.split("}")[0] + "}"
    return ""


def parse_track_point(track_point: ET.Element, namespace: str) -> TrackPoint | None:
    latitude = track_point.get("lat")
    longitude = track_point.get("lon")
    time_element = track_point.find(f"{namespace}time")
    if not latitude or not longitude or time_element is None or not time_element.text:
        return None
    try:
        captured_at = datetime.strptime(time_element.text.rstrip("Z"), "%Y-%m-%dT%H:%M:%S")
        return TrackPoint(float(latitude), float(longitude), captured_at)
    except ValueError:
        return None


def parse_gpx_track(gpx_path: str | Path) -> DemoTrack | None:
    """Read only the metadata needed to select timed segments from one GPX file."""
    path = Path(gpx_path)
    try:
        tree = ET.parse(path)
    except (ET.ParseError, OSError):
        return None
    root = tree.getroot()
    namespace = gpx_namespace(root)
    previous: TrackPoint | None = None
    start: datetime | None = None
    end: datetime | None = None
    timed_segment_count = 0
    for track_point in root.iter(f"{namespace}trkpt"):
        current = parse_track_point(track_point, namespace)
        if current is None:
            continue
        start = current.captured_at if start is None else min(start, current.captured_at)
        end = current.captured_at if end is None else max(end, current.captured_at)
        if previous is not None and current.captured_at > previous.captured_at:
            timed_segment_count += 1
        previous = current

    if start is None or end is None:
        return None
    return DemoTrack(
        path=path,
        timed_segment_count=timed_segment_count,
        start=start,
        end=end,
    )


def collect_tracks(gpx_dir: str | Path) -> list[DemoTrack]:
    """Return compact track metadata in deterministic filename order."""
    paths = sorted(glob.glob(os.path.join(str(gpx_dir), "**", "*.gpx"), recursive=True))
    return [track for path in paths if (track := parse_gpx_track(path)) is not None]


def load_timed_segment(
    track: DemoTrack,
    segment_index: int,
) -> tuple[TrackPoint, TrackPoint]:
    """Load one selected segment without retaining another track's points."""
    if segment_index < 0 or segment_index >= track.timed_segment_count:
        raise IndexError(f"Timed segment index {segment_index} is outside {track.path}")

    try:
        tree = ET.parse(track.path)
    except (ET.ParseError, OSError) as exception:
        raise RuntimeError(f"Could not reload GPX track {track.path}") from exception

    root = tree.getroot()
    namespace = gpx_namespace(root)
    previous: TrackPoint | None = None
    current_segment_index = 0
    for track_point in root.iter(f"{namespace}trkpt"):
        current = parse_track_point(track_point, namespace)
        if current is None:
            continue
        if previous is not None and current.captured_at > previous.captured_at:
            if current_segment_index == segment_index:
                return previous, current
            current_segment_index += 1
        previous = current

    raise RuntimeError(
        f"Timed segment {segment_index} is no longer available in GPX track {track.path}"
    )


def offset_point(
    latitude: float,
    longitude: float,
    max_meters: float,
    rng: random.Random,
) -> tuple[float, float]:
    """Apply a random offset of 0–max_meters in a random bearing."""
    distance = rng.uniform(0, max_meters)
    bearing = rng.uniform(0, 2 * math.pi)
    latitude_delta = (distance * math.cos(bearing)) / EARTH_RADIUS_M
    longitude_delta = (
        (distance * math.sin(bearing))
        / (EARTH_RADIUS_M * math.cos(math.radians(latitude)))
    )
    return latitude + math.degrees(latitude_delta), longitude + math.degrees(longitude_delta)


def interpolate_segment(
    before: TrackPoint,
    after: TrackPoint,
    ratio: float,
) -> tuple[float, float, datetime]:
    latitude = before.latitude + ratio * (after.latitude - before.latitude)
    longitude = before.longitude + ratio * (after.longitude - before.longitude)
    captured_at = before.captured_at + ratio * (after.captured_at - before.captured_at)
    return latitude, longitude, captured_at


def choose_track_position(
    tracks: list[DemoTrack],
    rng: random.Random,
) -> tuple[float, float, datetime]:
    track = rng.choice(tracks)
    before, after = load_timed_segment(
        track,
        rng.randrange(track.timed_segment_count),
    )
    ratio = rng.uniform(MIN_SEGMENT_RATIO, MAX_SEGMENT_RATIO)
    return interpolate_segment(before, after, ratio)


def standalone_capture_gaps(tracks: list[DemoTrack]) -> tuple[CaptureGap, ...]:
    """Build second-resolution gaps that cannot match any track time window."""
    windows = sorted((track.start, track.end) for track in tracks)
    if not windows:
        return ()

    merged_windows: list[tuple[datetime, datetime]] = []
    for start, end in windows:
        if merged_windows and start <= merged_windows[-1][1]:
            previous_start, previous_end = merged_windows[-1]
            merged_windows[-1] = previous_start, max(previous_end, end)
        else:
            merged_windows.append((start, end))

    gaps: list[CaptureGap] = []
    for (_, previous_end), (next_start, _) in zip(merged_windows, merged_windows[1:]):
        gap_start = previous_end + ONE_SECOND
        gap_end = next_start - ONE_SECOND
        if gap_start <= gap_end:
            seconds = int((gap_end - gap_start).total_seconds()) + 1
            gaps.append(CaptureGap(gap_start, seconds))
    return tuple(gaps)


def choose_standalone_capture_time(
    gaps: tuple[CaptureGap, ...],
    latest_track_time: datetime,
    rng: random.Random,
) -> datetime:
    if not gaps:
        return latest_track_time + STANDALONE_FALLBACK_OFFSET

    selected_second = rng.randrange(sum(gap.seconds for gap in gaps))
    for gap in gaps:
        if selected_second < gap.seconds:
            return gap.start + timedelta(seconds=selected_second)
        selected_second -= gap.seconds
    raise AssertionError("Standalone capture gap selection exceeded its weighted range")


def is_track_linked_photo(photo_index: int) -> bool:
    return photo_index % LINKED_PHOTO_MODULUS == LINKED_PHOTO_REMAINDER


def read_completed_index(progress_path: Path) -> int:
    try:
        return max(0, int(progress_path.read_text(encoding="utf-8").strip()))
    except (OSError, ValueError):
        return 0


def write_completed_index(progress_path: Path, completed_index: int) -> None:
    temporary_path = progress_path.with_suffix(".tmp")
    temporary_path.write_text(f"{completed_index}\n", encoding="utf-8")
    temporary_path.replace(progress_path)


def prepare_render_task(
    photo_index: int,
    output_dir: Path,
    eligible_tracks: list[DemoTrack],
    position_tracks: list[DemoTrack],
    standalone_gaps: tuple[CaptureGap, ...],
    latest_track_time: datetime,
) -> tuple[DemoPhotoRenderTask, bool]:
    rng = random.Random(f"mtl-demo-photo-{photo_index}")
    linked = is_track_linked_photo(photo_index)
    if linked:
        latitude, longitude, captured_at = choose_track_position(eligible_tracks, rng)
        latitude, longitude = offset_point(latitude, longitude, MAX_OFFSET_METERS, rng)
    else:
        latitude, longitude, _ = choose_track_position(position_tracks, rng)
        captured_at = choose_standalone_capture_time(standalone_gaps, latest_track_time, rng)

    return (
        DemoPhotoRenderTask(
            photo_index=photo_index,
            latitude=latitude,
            longitude=longitude,
            captured_at=captured_at,
            output_path=output_dir / f"demo_photo_{photo_index:05d}.jpg",
            rng=rng,
        ),
        linked,
    )


def generate_demo_photos(
    gpx_dir: str | Path,
    media_output_dir: str | Path,
    num_photos: int,
    eligible_track_count: int = 0,
) -> tuple[int, int]:
    output_dir = Path(media_output_dir) / "demo-photos"
    output_dir.mkdir(parents=True, exist_ok=True)

    existing = set(output_dir.glob("demo_photo_*.jpg"))
    progress_path = output_dir / PROGRESS_FILE_NAME
    completed_index = read_completed_index(progress_path)

    existing_indices: set[int] = set()
    for path in existing:
        try:
            existing_indices.add(int(path.stem.replace("demo_photo_", "")))
        except ValueError:
            pass

    missing_completed_indices = [
        index
        for index in range(1, min(completed_index, num_photos) + 1)
        if index not in existing_indices
    ]
    pending_indices = missing_completed_indices + list(
        range(completed_index + 1, num_photos + 1)
    )
    if not pending_indices:
        print(
            f"✅ Demo photos: format v{DEMO_PHOTO_FORMAT_VERSION}, "
            f"{num_photos}/{num_photos} already exist."
        )
        return 0, 0

    tracks = collect_tracks(gpx_dir)
    if not tracks:
        raise RuntimeError("No timed track points found in GPX files")

    eligible_count = (
        len(tracks)
        if eligible_track_count <= 0
        else min(eligible_track_count, len(tracks))
    )
    position_tracks = [track for track in tracks if track.timed_segment_count > 0]
    eligible_tracks = [
        track for track in tracks[:eligible_count] if track.timed_segment_count > 0
    ]
    if not eligible_tracks:
        raise RuntimeError("No eligible GPX tracks contain an increasing timed segment")

    standalone_gaps = standalone_capture_gaps(tracks)
    latest_track_time = max(track.end for track in tracks)
    print(
        f"📷 Demo photos: generating {len(pending_indices)} labelled JPEG files "
        f"(format v{DEMO_PHOTO_FORMAT_VERSION}, target: {num_photos}, "
        f"eligible tracks: {len(eligible_tracks)}/{len(tracks)})…"
    )

    linked_generated = 0
    standalone_generated = 0
    highest_completed_index = completed_index

    # Keep the catalog compact and render one photo at a time. The selected GPX
    # segment is loaded on demand, so the Java server does not share its cgroup with
    # hundreds of thousands of retained Python track-point and segment objects.
    for generated_count, photo_index in enumerate(pending_indices, start=1):
        task, linked = prepare_render_task(
            photo_index,
            output_dir,
            eligible_tracks,
            position_tracks,
            standalone_gaps,
            latest_track_time,
        )
        if linked:
            linked_generated += 1
        else:
            standalone_generated += 1

        completed_index = render_demo_photo(task)
        highest_completed_index = max(highest_completed_index, completed_index)
        # Persist every atomically published photo. A restart can repeat at most
        # the photo that completed immediately before this progress write.
        write_completed_index(progress_path, highest_completed_index)
        if (
            generated_count % PHOTO_GENERATION_PROGRESS_INTERVAL == 0
            or generated_count == len(pending_indices)
        ):
            print(f"   … generated {generated_count}/{len(pending_indices)}")

    write_completed_index(progress_path, max(highest_completed_index, num_photos))
    print(
        f"✅ Done. Demo photos use format v{DEMO_PHOTO_FORMAT_VERSION} at "
        f"{DEMO_PHOTO_WIDTH}×{DEMO_PHOTO_HEIGHT}; generated "
        f"linked={linked_generated}, standalone={standalone_generated}."
    )
    return linked_generated, standalone_generated


def main() -> None:
    if len(sys.argv) not in (4, 5):
        print(
            f"Usage: {sys.argv[0]} "
            "<gpx_dir> <media_output_dir> <num_photos> [eligible_track_count]"
        )
        sys.exit(1)

    try:
        generate_demo_photos(
            sys.argv[1],
            sys.argv[2],
            int(sys.argv[3]),
            int(sys.argv[4]) if len(sys.argv) == 5 else 0,
        )
    except (RuntimeError, ValueError) as exception:
        print(f"⚠️  Demo photos: {exception}.")
        sys.exit(1)


if __name__ == "__main__":
    main()
