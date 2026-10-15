#!/usr/bin/env python3
"""
generate_demo_photos.py — Create geotagged placeholder JPEG photos for demo mode.

Usage:
    python3 generate_demo_photos.py \
        <gpx_dir> <media_output_dir> <num_photos> [eligible_track_count]

- Validates packaged track metadata against <gpx_dir>; parses GPX on drift.
- Generates a deterministic split of track-linked and standalone Porto photos.
- Track-linked photos have capture times inside an eligible GPX track and are
  positioned no more than 25 m from its interpolated route position.
- Standalone photos use route-derived land positions and capture times that do
  not overlap any GPX track.
- Writes 1920x1440 JPEGs with GPS coordinates, DateTimeOriginal, and GPS time to
  <media_output_dir>/demo-photos/.
- A versioned progress marker limits restart work and upgrades legacy photos.
- The startup CLI defaults to four render workers (maximum six) and a deadline
  capped at ten minutes.

Dependencies (available in the Docker image):
    pip install Pillow piexif
"""

from __future__ import annotations

import glob
import math
import multiprocessing
import os
import random
import signal
import sys
import time
import xml.etree.ElementTree as ET
from collections import deque
from concurrent.futures import ProcessPoolExecutor
from contextlib import contextmanager
from dataclasses import dataclass
from datetime import datetime, timedelta
from pathlib import Path

from photo_placeholder import generate_placeholder_jpeg
from demo_logging import get_logger

log = get_logger("DemoPhotos")


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
DEFAULT_PHOTO_COUNT = 200
DEFAULT_WORKERS = 4
MAX_WORKERS = 6
TASKS_PER_WORKER = 2
TIMEOUT_BASE_SECONDS = 60
TIMEOUT_SECONDS_PER_PHOTO = 0.25
MAX_TIMEOUT_SECONDS = 600
DEFAULT_DEMO_ZIP = Path("/app/demo/porto_taxi_service_gpx_extract.zip")
DEFAULT_TRACK_CATALOG = Path("/app/demo/demo-track-catalog.json.gz")


class GenerationInterrupted(BaseException):
    def __init__(self, signum: int):
        self.signum = signum


def interrupt_generation(signum, _frame) -> None:
    signal.signal(signal.SIGTERM, signal.SIG_IGN)
    signal.signal(signal.SIGINT, signal.SIG_IGN)
    raise GenerationInterrupted(signum)


def initialize_render_worker() -> None:
    # Workers must terminate directly; only the coordinator owns pool cleanup.
    for signum in (signal.SIGTERM, signal.SIGINT, signal.SIGALRM):
        signal.signal(signum, signal.SIG_DFL)


def configured_integer(name: str, value: str, default: int | None, *, minimum: int = 1,
                       maximum: int | None = None) -> int | None:
    try:
        result = int(value)
        if result < minimum:
            raise ValueError(f"must be at least {minimum}")
    except ValueError as exception:
        default_description = "the calculated default" if default is None else str(default)
        log.warning("%s: %s: %s; using %s.", name, type(exception).__name__, exception, default_description)
        return default
    if maximum is not None and result > maximum:
        log.warning("%s=%s exceeds %s; using %s.", name, result, maximum, maximum)
        return maximum
    return result


@contextmanager
def generation_deadline(seconds: int):
    def expired(_signum, _frame):
        raise TimeoutError(f"Demo photo generation exceeded {seconds}s; resume on next startup")

    previous_handler = signal.signal(signal.SIGALRM, expired)
    signal.alarm(seconds)
    try:
        yield
    finally:
        signal.alarm(0)
        signal.signal(signal.SIGALRM, previous_handler)


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
    except Exception as exception:
        log.error("Demo photo render failed: index=%s, file=%s, error=%s: %s.",
                  task.photo_index, task.output_path.name, type(exception).__name__, exception,
                  exc_info=True)
        raise
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


def parse_gpx_track(gpx_path: str | Path, source=None) -> DemoTrack | None:
    """Read only the metadata needed to select timed segments from one GPX file."""
    path = Path(gpx_path)
    try:
        tree = ET.parse(path if source is None else source)
    except (ET.ParseError, OSError) as exception:
        log.warning("Rejected GPX %s: %s: %s", path.name, type(exception).__name__, exception)
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
    if not progress_path.exists():
        return 0
    try:
        return max(0, int(progress_path.read_text(encoding="utf-8").strip()))
    except (OSError, ValueError) as exception:
        log.warning("Could not read photo progress: %s: %s", type(exception).__name__, exception)
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
    *,
    workers: int = 1,
    catalog_path: Path = DEFAULT_TRACK_CATALOG,
    zip_path: Path = DEFAULT_DEMO_ZIP,
    timeout_seconds: int | None = None,
) -> tuple[int, int]:
    if num_photos <= 0:
        log.info("Demo photo generation disabled (count=0).")
        return 0, 0
    output_dir = Path(media_output_dir) / "demo-photos"
    output_dir.mkdir(parents=True, exist_ok=True)
    leftovers = list(output_dir.glob("demo_photo_*.jpg.part-*"))
    for leftover in leftovers:
        leftover.unlink(missing_ok=True)
    if leftovers:
        log.info("Removed %s unfinished demo photo temporary files.", len(leftovers))

    existing = set(output_dir.glob("demo_photo_*.jpg"))
    progress_path = output_dir / PROGRESS_FILE_NAME
    completed_index = read_completed_index(progress_path)

    existing_indices: set[int] = set()
    for path in existing:
        try:
            existing_indices.add(int(path.stem.replace("demo_photo_", "")))
        except ValueError as exception:
            log.warning("Ignoring invalid demo photo name %s: %s: %s", path.name, type(exception).__name__, exception)

    missing_completed_indices = [
        index
        for index in range(1, min(completed_index, num_photos) + 1)
        if index not in existing_indices
    ]
    pending_indices = missing_completed_indices + list(
        range(completed_index + 1, num_photos + 1)
    )
    if not pending_indices:
        log.info("Demo photos: format v%s, %s/%s already exist; skipping catalog loading and rendering.",
                 DEMO_PHOTO_FORMAT_VERSION, num_photos, num_photos)
        return 0, 0

    calculated_timeout = min(MAX_TIMEOUT_SECONDS, TIMEOUT_BASE_SECONDS + math.ceil(
        TIMEOUT_SECONDS_PER_PHOTO * len(pending_indices)))
    deadline = calculated_timeout if timeout_seconds is None else timeout_seconds
    started = time.monotonic()
    log.info("Demo photo generation starting: target=%s, missing=%s, workers=%s, deadline=%ss.",
             num_photos, len(pending_indices), workers, deadline)
    log.info("Demo photo preparation invoked; generation may take a few minutes. Progress will be logged below.")
    with generation_deadline(deadline):
        result = generate_pending_photos(gpx_dir, output_dir, num_photos,
                                        eligible_track_count, pending_indices,
                                        completed_index, existing_indices,
                                        workers, catalog_path, zip_path)
    log.info("Demo photo generation completed in %.2fs; generated linked=%s, standalone=%s.",
             time.monotonic() - started, result[0], result[1])
    return result


def generate_pending_photos(
    gpx_dir: str | Path,
    output_dir: Path,
    num_photos: int,
    eligible_track_count: int,
    pending_indices: list[int],
    completed_index: int,
    existing_indices: set[int],
    workers: int,
    catalog_path: Path,
    zip_path: Path,
) -> tuple[int, int]:
    # A lazy import keeps the catalog builder and the parser shared without a cycle.
    from demo_track_catalog import load_tracks
    tracks = load_tracks(Path(gpx_dir), catalog_path, zip_path)
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
    log.info("Rendering %s demo JPEGs: format=v%s, size=%sx%s, eligible tracks=%s/%s, queue limit=%s.",
             len(pending_indices), DEMO_PHOTO_FORMAT_VERSION, DEMO_PHOTO_WIDTH, DEMO_PHOTO_HEIGHT,
             len(eligible_tracks), len(tracks), workers * TASKS_PER_WORKER)

    linked_generated = 0
    standalone_generated = 0
    progress_path = output_dir / PROGRESS_FILE_NAME
    confirmed_indices = {index for index in existing_indices if index <= completed_index}
    contiguous_index = 0
    while contiguous_index + 1 in confirmed_indices:
        contiguous_index += 1
    write_completed_index(progress_path, contiguous_index)

    def tasks():
        for photo_index in pending_indices:
            yield prepare_render_task(
                photo_index, output_dir, eligible_tracks, position_tracks,
                standalone_gaps, latest_track_time)

    def rendered_tasks():
        if workers == 1:
            for task, linked in tasks():
                yield render_demo_photo(task), linked
            return
        # Linux forkserver workers never inherit or receive the full track catalog.
        executor = ProcessPoolExecutor(max_workers=workers,
                                       mp_context=multiprocessing.get_context("forkserver"),
                                       initializer=initialize_render_worker)
        pending = deque()
        completed = False
        previous_children = set(multiprocessing.active_children())
        try:
            for task, linked in tasks():
                pending.append((executor.submit(render_demo_photo, task), linked))
                if len(pending) >= workers * TASKS_PER_WORKER:
                    future, linked = pending.popleft()
                    yield future.result(), linked
            while pending:
                future, linked = pending.popleft()
                yield future.result(), linked
            completed = True
        finally:
            if completed:
                executor.shutdown(wait=True)
            else:
                # Python 3.14's public API also covers a stuck native image codec.
                children = set(multiprocessing.active_children()) - previous_children
                executor.kill_workers()
                for child in children:
                    child.join()

    results = rendered_tasks()
    try:
        for generated_count, (photo_index, linked) in enumerate(results, start=1):
            if linked:
                linked_generated += 1
            else:
                standalone_generated += 1
            confirmed_indices.add(photo_index)
            while contiguous_index + 1 in confirmed_indices:
                contiguous_index += 1
            write_completed_index(progress_path, contiguous_index)
            if (generated_count % PHOTO_GENERATION_PROGRESS_INTERVAL == 0
                    or generated_count == len(pending_indices)):
                log.info("Photo progress: generated=%s/%s, contiguous completed index=%s, target=%s.",
                         generated_count, len(pending_indices), contiguous_index, num_photos)
    finally:
        results.close()
    return linked_generated, standalone_generated


def main() -> None:
    if len(sys.argv) not in (4, 5):
        log.error("Usage: %s <gpx_dir> <media_output_dir> <num_photos> [eligible_track_count]", sys.argv[0])
        sys.exit(1)

    signal.signal(signal.SIGTERM, interrupt_generation)
    signal.signal(signal.SIGINT, interrupt_generation)
    photo_count = configured_integer("DEMO_PHOTO_COUNT", sys.argv[3], DEFAULT_PHOTO_COUNT,
                                     minimum=0)
    workers = configured_integer("DEMO_PHOTO_WORKERS", os.environ.get("DEMO_PHOTO_WORKERS", str(DEFAULT_WORKERS)),
                                 DEFAULT_WORKERS, maximum=MAX_WORKERS)
    eligible_count = configured_integer("DEMO_TARGET_TRACK_COUNT", sys.argv[4] if len(sys.argv) == 5 else "0",
                                        0, minimum=0)
    # Resolve the calculated default after determining missing photos.
    timeout_value = os.environ.get("DEMO_PHOTO_GENERATION_TIMEOUT_SECONDS", "")
    timeout_seconds = None
    if timeout_value:
        timeout_seconds = configured_integer("DEMO_PHOTO_GENERATION_TIMEOUT_SECONDS", timeout_value,
                                              None, maximum=MAX_TIMEOUT_SECONDS)
    try:
        generate_demo_photos(
            sys.argv[1],
            sys.argv[2],
            photo_count,
            eligible_count,
            workers=workers,
            timeout_seconds=timeout_seconds,
        )
    except GenerationInterrupted as exception:
        log.info("Demo photo generation stopped by signal %s; workers reaped; resume on next startup.", exception.signum)
        sys.exit(128 + exception.signum)
    except Exception as exception:
        log.warning("Demo photo generation failed: %s: %s; workers reaped; resume on next startup.",
                    type(exception).__name__, exception)
        sys.exit(1)


if __name__ == "__main__":
    main()
