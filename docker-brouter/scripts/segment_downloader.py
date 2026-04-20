#!/usr/bin/env python3
"""
segment_downloader.py — background downloader for BRouter rd5 segment files.

BRouter partitions the world into 5°×5° tiles, each named e.g. "E5_N45.rd5"
(west-south corner; E for east-of-prime-meridian, N for north-of-equator).
Missing tiles are fetched from SEGMENTS_BASE_URL (brouter.de) on demand.

All thresholds / URLs live in constants.py.
"""
import math
import queue
import threading
import time
import urllib.error
import urllib.request
from pathlib import Path

from constants import (
    DOWNLOAD_RETRIES, DOWNLOAD_TIMEOUT_SEC, HTTP_USER_AGENT,
    SEGMENT_DEGREES, SEGMENTS_BASE_URL,
)


def log(msg: str) -> None:
    print(f"[segments] {msg}", flush=True)


def tile_name_for(lat: float, lng: float) -> str:
    """Return BRouter rd5 filename for the 5°×5° cell containing (lat, lng)."""
    # BRouter segment naming: origin is the SW corner of the tile.
    # Longitude: "W{n}" for negative, "E{n}" for positive; BRouter uses the shifted
    # coordinate system where E0 corresponds to lng=0..5. Files on brouter.de are
    # named like "W10_N45.rd5" with step of 5 degrees.
    lng_floor = int(math.floor(lng / SEGMENT_DEGREES) * SEGMENT_DEGREES)
    lat_floor = int(math.floor(lat / SEGMENT_DEGREES) * SEGMENT_DEGREES)
    lng_tag = f"E{lng_floor}" if lng_floor >= 0 else f"W{-lng_floor}"
    lat_tag = f"N{lat_floor}" if lat_floor >= 0 else f"S{-lat_floor}"
    return f"{lng_tag}_{lat_tag}.rd5"


class SegmentDownloader:
    """Queue-backed background downloader, safe for concurrent /prewarm calls.

    Uses a PriorityQueue so that on-demand (urgent) requests are processed
    before background (bulk) prewarm tiles. Priority 0 = urgent, 1 = normal.
    """

    PRIORITY_URGENT = 0
    PRIORITY_NORMAL = 1

    def __init__(self, segments_dir: Path) -> None:
        self.segments_dir = segments_dir
        self._queue: queue.PriorityQueue[tuple[int, int, str]] = queue.PriorityQueue()
        self._seq = 0  # tie-breaker for same-priority items (FIFO within priority)
        self._queued: set[str] = set()  # ever seen — prevents re-queueing
        self._in_progress: set[str] = set()
        self._completed: set[str] = set()
        self._failed: dict[str, str] = {}
        self._known_404: set[str] = set()  # tiles confirmed as 404 — never retry
        self._lock = threading.Lock()

    def start(self) -> None:
        threading.Thread(target=self._worker, name="segment-downloader", daemon=True).start()

    # ── Public API ───────────────────────────────────────────────────

    def enqueue_bbox(self, min_lng: float, min_lat: float,
                     max_lng: float, max_lat: float,
                     urgent: bool = False) -> list[str]:
        """Enqueue every rd5 tile intersecting the given bbox.

        When ``urgent=True``, tiles are inserted at the front of the queue
        (priority 0) so they are downloaded before background prewarm tiles.
        Already-queued tiles are re-prioritised when urgent.
        Returns newly queued tile names.
        """
        priority = self.PRIORITY_URGENT if urgent else self.PRIORITY_NORMAL
        tiles: list[str] = []
        lng = math.floor(min_lng / SEGMENT_DEGREES) * SEGMENT_DEGREES
        while lng <= max_lng:
            lat = math.floor(min_lat / SEGMENT_DEGREES) * SEGMENT_DEGREES
            while lat <= max_lat:
                name = tile_name_for(lat, lng)
                if self._enqueue(name, priority):
                    tiles.append(name)
                lat += SEGMENT_DEGREES
            lng += SEGMENT_DEGREES
        tag = "URGENT" if urgent else "background"
        log(f"prewarm ({tag}) queued {len(tiles)} new tiles for bbox=[{min_lng},{min_lat},{max_lng},{max_lat}]")
        return tiles

    def snapshot(self) -> dict:
        with self._lock:
            on_disk = sorted(p.name for p in self.segments_dir.glob("*.rd5"))
            return {
                "segmentsOnDisk": len(on_disk),
                "segmentsQueued": self._queue.qsize(),
                "segmentsInProgress": sorted(self._in_progress),
                "segmentsCompletedThisRun": sorted(self._completed),
                "segmentsFailed": dict(self._failed),
                "segmentsKnown404": len(self._known_404),
            }

    # ── Internals ────────────────────────────────────────────────────

    def _enqueue(self, name: str, priority: int) -> bool:
        with self._lock:
            if name in self._known_404:
                return False  # permanently missing tile — skip
            if (self.segments_dir / name).exists():
                self._queued.add(name)
                return False  # already on disk — skip
            if name in self._queued:
                if priority == self.PRIORITY_URGENT:
                    # Re-insert with urgent priority so it jumps the queue.
                    # The worker skips duplicates via on-disk / completed checks.
                    self._seq += 1
                    self._queue.put((priority, self._seq, name))
                    log(f"re-prioritised {name} to URGENT")
                return False
            self._queued.add(name)
            self._seq += 1
            self._queue.put((priority, self._seq, name))
            return True

    def _worker(self) -> None:
        while True:
            _priority, _seq, name = self._queue.get()
            try:
                self._download(name)
            except _Permanent404 as e:
                with self._lock:
                    self._known_404.add(name)
                    self._failed[name] = str(e)
                log(f"tile {name} does not exist (404) — skipped permanently")
            except Exception as e:
                with self._lock:
                    self._failed[name] = str(e)
                log(f"download failed name={name} err={e}")
            finally:
                with self._lock:
                    self._in_progress.discard(name)
                self._queue.task_done()

    def _download(self, name: str) -> None:
        target = self.segments_dir / name
        if target.exists():
            with self._lock:
                self._completed.add(name)
            return
        with self._lock:
            if name in self._known_404:
                return  # another attempt already established this is 404
            self._in_progress.add(name)

        url = f"{SEGMENTS_BASE_URL}/{name}"
        tmp = target.with_suffix(".rd5.part")
        last_err: Exception | None = None
        for attempt in range(1, DOWNLOAD_RETRIES + 1):
            try:
                log(f"GET {url} (attempt {attempt}/{DOWNLOAD_RETRIES})")
                req = urllib.request.Request(url, headers={"User-Agent": HTTP_USER_AGENT})
                with urllib.request.urlopen(req, timeout=DOWNLOAD_TIMEOUT_SEC) as resp:
                    with open(tmp, "wb") as out:
                        while True:
                            chunk = resp.read(1024 * 64)
                            if not chunk:
                                break
                            out.write(chunk)
                tmp.replace(target)
                with self._lock:
                    self._completed.add(name)
                    self._failed.pop(name, None)
                log(f"downloaded {name} ({target.stat().st_size} bytes)")
                return
            except urllib.error.HTTPError as e:
                if e.code == 404:
                    # Tile does not exist on the server (ocean, etc.) — no point retrying.
                    try:
                        if tmp.exists():
                            tmp.unlink()
                    except OSError:
                        pass
                    raise _Permanent404(f"HTTP 404: {name} does not exist on server")
                last_err = e
                log(f"HTTP {e.code} for {name} (attempt {attempt}/{DOWNLOAD_RETRIES})")
                time.sleep(2.0 * attempt)
            except Exception as e:
                last_err = e
                log(f"error downloading {name} (attempt {attempt}/{DOWNLOAD_RETRIES}): {e}")
                time.sleep(2.0 * attempt)
                try:
                    if tmp.exists():
                        tmp.unlink()
                except OSError:
                    pass
        assert last_err is not None
        raise last_err


class _Permanent404(Exception):
    """Raised when a tile returns HTTP 404 — it will never exist."""
    pass
