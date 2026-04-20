#!/usr/bin/env python3
"""
orchestrator.py — Map-server entrypoint orchestrator

Starts nginx immediately (container becomes healthy right away), then
manages versioned tile files and updates a live progress status file.

STATELESS DESIGN: The orchestrator does not decide scope at startup.
It waits for a kickoff request from the Java backend that carries the
scope in the URL path (PUT /kickoff/demo or PUT /kickoff/prod).
On each kickoff it:
  1. Seeds pre-built tiles from /data-prebuilt/{scope}/ into /data/{scope}/
  2. Checks for existing ready tiles, incomplete downloads, or starts fresh
  3. All functions receive scope_dir as an explicit parameter

Two independent scope directories: /data/demo/ and /data/prod/.
Each uses versioned filenames (YYYYMMDD.pmtiles) with symlinks
(planet.pmtiles → YYYYMMDD.pmtiles).

Status is written atomically to /tmp/map-status.json, served by nginx
at GET /status so the Java backend (and any other consumer) can poll it.
"""

import json
import os
import queue
import re
import signal
import subprocess
import sys
import threading
import time
import urllib.request
import http.server
from datetime import date, timedelta
from pathlib import Path

sys.path.insert(0, os.path.dirname(__file__))
from common import resolve_latest_url, protomaps_build_url, _HTTP_HEADERS

# ── Configuration (from environment) ─────────────────────────────────

DATA_DIR = Path("/data")
PREBUILT_DIR = Path("/data-prebuilt")

MAP_DOWNLOAD_URL = os.environ.get("MAP_DOWNLOAD_URL", "")
MAP_AREA_BBOX = os.environ.get("MAP_AREA_BBOX", "")  # "west,south,east,north" for area-only extract
LOWZOOM_MAXZOOM = os.environ.get("LOWZOOM_MAXZOOM", "6")
PORT = os.environ.get("PORT", "8081")

STATUS_FILE = Path("/tmp/map-status.json")

# Pattern to match versioned tile files: YYYYMMDD.pmtiles
_DATE_RE = re.compile(r"^(\d{8})\.pmtiles$")

# ── Globals for process management ───────────────────────────────────

_children: list[subprocess.Popen] = []
_lock = threading.Lock()
_kickoff_queue: queue.Queue[str] = queue.Queue()


# ── Versioned file helpers ───────────────────────────────────────────

def tile_file(scope_dir: Path, date_str: str) -> Path:
    return scope_dir / f"{date_str}.pmtiles"

def done_file(scope_dir: Path, date_str: str) -> Path:
    return scope_dir / f"{date_str}.pmtiles.done"

def url_file(scope_dir: Path, date_str: str) -> Path:
    return scope_dir / f"{date_str}.pmtiles.url"

def lowzoom_file(scope_dir: Path, date_str: str) -> Path:
    return scope_dir / f"{date_str}-lowzoom.pmtiles"

def lowzoom_done_file(scope_dir: Path, date_str: str) -> Path:
    return scope_dir / f"{date_str}-lowzoom.pmtiles.done"


def parse_date_from_url(url: str) -> str:
    """Extract YYYYMMDD from a Protomaps URL like https://build.protomaps.com/20260404.pmtiles"""
    m = re.search(r"(\d{8})\.pmtiles", url)
    if not m:
        raise ValueError(f"Cannot parse build date from URL: {url}")
    return m.group(1)


def find_active_symlink(scope_dir: Path) -> str | None:
    """Return the date string if planet.pmtiles symlink points to a complete versioned file."""
    planet_link = scope_dir / "planet.pmtiles"
    if not planet_link.is_symlink():
        return None
    target = os.readlink(planet_link)  # e.g. "20260404.pmtiles"
    m = _DATE_RE.match(target)
    if not m:
        return None
    ds = m.group(1)
    if tile_file(scope_dir, ds).exists() and done_file(scope_dir, ds).exists():
        return ds
    return None


def find_incomplete_download(scope_dir: Path) -> str | None:
    """Find a YYYYMMDD.pmtiles file without a .done sentinel (interrupted download)."""
    for f in sorted(scope_dir.glob("*.pmtiles")):
        if f.is_symlink():
            continue
        m = _DATE_RE.match(f.name)
        if m:
            ds = m.group(1)
            if not done_file(scope_dir, ds).exists():
                return ds
    return None


def create_symlinks(scope_dir: Path, date_str: str) -> None:
    """Atomically create/replace planet.pmtiles and world-lowzoom.pmtiles symlinks."""
    planet_link = scope_dir / "planet.pmtiles"
    lowzoom_link = scope_dir / "world-lowzoom.pmtiles"
    _atomic_symlink(f"{date_str}.pmtiles", planet_link)
    lz = lowzoom_file(scope_dir, date_str)
    if lz.exists() and lowzoom_done_file(scope_dir, date_str).exists():
        _atomic_symlink(f"{date_str}-lowzoom.pmtiles", lowzoom_link)


def _atomic_symlink(target_name: str, link_path: Path) -> None:
    """Create a symlink atomically: write a temp link, then rename over the old one."""
    tmp = link_path.with_suffix(".tmp")
    if tmp.exists() or tmp.is_symlink():
        tmp.unlink()
    os.symlink(target_name, tmp)
    os.rename(tmp, link_path)
    log(f"  Symlink: {link_path.name} → {target_name}")


# ── Pre-built tile seeding ───────────────────────────────────────────

def seed_prebuilt(scope: str) -> None:
    """Seed pre-built tiles from /data-prebuilt/{scope}/ into /data/{scope}/.

    Skipped if:
      - No pre-built directory exists for this scope
      - The target already has a planet.pmtiles symlink (already seeded)
    Uses cp -a to preserve symlinks.
    """
    src = PREBUILT_DIR / scope
    dst = DATA_DIR / scope
    planet_link = dst / "planet.pmtiles"

    if not src.exists() or not any(src.iterdir()):
        log(f"No pre-built tiles for scope '{scope}' — skipping seed")
        return

    if planet_link.is_symlink() or planet_link.exists():
        log(f"Scope '{scope}' already has planet.pmtiles — skipping seed")
        return

    log(f"Seeding pre-built tiles from {src} into {dst} ...")
    dst.mkdir(parents=True, exist_ok=True)
    # Use cp -a to preserve symlinks
    subprocess.run(
        ["cp", "-a"] + [str(f) for f in src.iterdir()] + [str(dst) + "/"],
        check=True,
    )
    log(f"Done seeding {scope} tiles.")
    # Ensure everything under dst is world-writable so NAS users can manage it
    subprocess.run(["chmod", "-R", "777", str(dst)], check=False)


# ── Kickoff Web Server ───────────────────────────────────────────────

_VALID_SCOPES = {"demo", "prod"}


def _parse_scope(path: str) -> str | None:
    """Extract scope from a kickoff URL path like /kickoff/demo → 'demo'."""
    parts = [p for p in path.strip("/").split("/") if p]
    if len(parts) == 2 and parts[0] == "kickoff" and parts[1] in _VALID_SCOPES:
        return parts[1]
    return None


class KickoffHandler(http.server.BaseHTTPRequestHandler):
    def do_POST(self):
        self._handle_kickoff()

    def do_PUT(self):
        self._handle_kickoff()

    def do_GET(self):
        self._handle_kickoff()

    def do_DELETE(self):
        scope = _parse_scope(self.path)
        if scope:
            scope_dir = DATA_DIR / scope
            log(f"Received DELETE {self.path} request — clearing {scope} tiles.")
            # Remove symlinks
            for name in ("planet.pmtiles", "world-lowzoom.pmtiles"):
                sl = scope_dir / name
                if sl.is_symlink() or sl.exists():
                    sl.unlink()
            # Remove all versioned tile files, sentinels, and url files
            for f in scope_dir.glob("*.pmtiles*"):
                if f.is_symlink():
                    continue
                f.unlink()
                log(f"  Removed {f.name}")
            # Wake up the background loop to re-process this scope
            _kickoff_queue.put(scope)

            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(b'{"status": "ok", "message": "Tiles deleted."}')
        else:
            self.send_response(404)
            self.end_headers()

    def _handle_kickoff(self):
        scope = _parse_scope(self.path)
        if scope:
            log(f"Received kick-off {self.command} {self.path} request — scope: {scope}")
            _kickoff_queue.put(scope)
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(b'{"status": "ok", "message": "Kick-off accepted."}')
        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, format, *args):
        # Use our own log format
        pass


def _start_kickoff_server():
    server = http.server.HTTPServer(('127.0.0.1', 8082), KickoffHandler)
    server.serve_forever()


# ── Logging ──────────────────────────────────────────────────────────

def log(msg: str) -> None:
    print(f"[map-server] {time.strftime('%Y-%m-%d %H:%M:%S')} {msg}", flush=True)


# ── Status file ──────────────────────────────────────────────────────

def write_status(
    phase: str,
    *,
    ready: bool = False,
    download_pct: int = 0,
    download_bytes: int = 0,
    download_total: int = 0,
    message: str = "",
) -> None:
    """Atomically write status JSON (write to .tmp, then rename)."""
    data = {
        "phase": phase,
        "ready": ready,
        "download_pct": min(download_pct, 100),
        "download_bytes": download_bytes,
        "download_total": download_total,
        "message": message,
    }
    tmp = STATUS_FILE.with_suffix(".tmp")
    tmp.write_text(json.dumps(data, indent=2))
    tmp.rename(STATUS_FILE)


# ── World-writable helpers ──────────────────────────────────────────

def _make_world_writable(path: Path) -> None:
    """Set 777 on directories, 666 on regular files so any user can read/write/delete."""
    try:
        if path.is_dir():
            os.chmod(path, 0o777)
        elif path.exists() and not path.is_symlink():
            os.chmod(path, 0o666)
    except Exception as e:
        log(f"WARNING: Could not chmod {path}: {e}")


# ── Permission checks ────────────────────────────────────────────────

def check_pmtiles_permissions(path: Path) -> None:
    """
    Warn loudly if a .pmtiles file (or its parent directory) is not
    world-readable.  nginx on Alpine runs as the 'nginx' user (non-root),
    so the file and directory must have the world-read (o+r) bit set.
    The orchestrator itself runs as root, so os.access() would always
    return True — we must inspect mode bits directly.
    """
    # Resolve symlinks before checking
    if path.is_symlink():
        path = path.resolve()

    # ── Check parent directory: needs o+r AND o+x bits ───────────────
    parent = path.parent
    try:
        parent_mode = parent.stat().st_mode
    except Exception as e:
        log(f"WARNING: Could not stat directory {parent}: {e}")
        return

    if not (parent_mode & 0o005 == 0o005):  # world read+execute
        write_status(
            "error",
            message=f"Directory {parent} is not accessible by nginx (mode: {oct(parent_mode & 0o777)}). "
                    f"Fix with: chmod o+rx {parent}",
        )
        log("════════════════════════════════════════════════════════════")
        log(f"  ERROR: Directory {parent} is NOT accessible by nginx")
        log(f"         Mode: {oct(parent_mode & 0o777)}  — needs at least 755 (o+rx)")
        log(f"         Vector maps will return 403 until this is fixed.")
        log(f"         Fix inside the container : chmod o+rx {parent}")
        log(f"         Fix on the host/NAS       : chmod 755 <host-path-to-maps-volume>")
        log("════════════════════════════════════════════════════════════")

    # ── Check file itself: needs o+r bit ─────────────────────────────
    if not path.exists():
        return  # file not present yet — download path handles this

    try:
        file_mode = path.stat().st_mode
    except Exception as e:
        log(f"WARNING: Could not stat {path}: {e}")
        return

    if not (file_mode & 0o004):  # world-readable
        write_status(
            "error",
            message=f"{path.name} is not readable by nginx (mode: {oct(file_mode & 0o777)}). "
                    f"Fix with: chmod o+r {path}",
        )
        log("════════════════════════════════════════════════════════════")
        log(f"  ERROR: {path} is NOT readable by nginx")
        log(f"         Mode: {oct(file_mode & 0o777)}  — needs at least 644 (o+r)")
        log(f"         Vector maps will return 403 until this is fixed.")
        log(f"         Fix inside the container : chmod o+r {path}")
        log(f"         Fix on the host/NAS       : chmod 644 <host-path-to-maps-volume>/{path.name}")
        log("════════════════════════════════════════════════════════════")


# ── URL resolution ───────────────────────────────────────────────────
# resolve_latest_url() is imported from common.py (single source of truth)


def get_content_length(url: str) -> int:
    """HEAD request to get Content-Length, with Range GET fallback for CDN hosts.
    Returns 0 if unavailable."""
    try:
        req = urllib.request.Request(url, method="HEAD", headers=_HTTP_HEADERS)
        with urllib.request.urlopen(req, timeout=30) as resp:
            length = int(resp.headers.get("Content-Length", "0"))
            if length > 0:
                return length
    except Exception:
        pass

    # Fallback: Range GET — CDNs (R2, S3, Cloudflare) populate Content-Range
    # even when HEAD omits Content-Length.  We request only the first byte so
    # almost no data is transferred.
    try:
        req = urllib.request.Request(url, headers={**_HTTP_HEADERS, "Range": "bytes=0-0"})
        with urllib.request.urlopen(req, timeout=30) as resp:
            content_range = resp.headers.get("Content-Range", "")  # e.g. "bytes 0-0/81234567890"
            if "/" in content_range:
                return int(content_range.split("/", 1)[1])
    except Exception:
        pass

    return 0


# ── Download with progress ───────────────────────────────────────────

def _monitor_download_progress(
    target: Path, total_holder: list, url: str, stop: threading.Event
) -> None:
    """Background thread: poll file size every 10s and update status JSON."""
    while not stop.is_set():
        total = total_holder[0]
        # If total is still unknown, keep retrying the HTTP fallback.
        if total == 0:
            fetched = get_content_length(url)
            if fetched > 0:
                total_holder[0] = fetched
                total = fetched
        current = target.stat().st_size if target.exists() else 0
        pct = int(current * 100 / total) if total > 0 else 0
        pct = min(pct, 100)
        write_status(
            "downloading",
            download_pct=pct,
            download_bytes=current,
            download_total=total,
            message=f"Downloading planet file ({pct}% — {current / (1024**3):.1f} / {total / (1024**3):.1f} GB)",
        )
        stop.wait(10)


def download_planet(scope_dir: Path, date_str: str, url: str) -> None:
    """Download the planet file using wget (for resume support), with progress monitoring."""
    target = tile_file(scope_dir, date_str)
    total = get_content_length(url)
    if total > 0:
        log(f"  Expected size: {total / (1024**3):.1f} GB ({total:,} bytes)")
    else:
        log("  Could not determine file size (Content-Length unavailable)")

    total_holder = [total]

    write_status("downloading", download_total=total, message="Starting download...")

    # Start progress monitor thread
    stop_event = threading.Event()
    monitor = threading.Thread(
        target=_monitor_download_progress,
        args=(target, total_holder, url, stop_event),
        daemon=True,
    )
    monitor.start()

    proc = subprocess.Popen(
        [
            "wget",
            f"--user-agent={_HTTP_HEADERS['User-Agent']}",
            "--continue",
            "--show-progress",
            "--progress=bar:force:noscroll",
            "--timeout=60",
            "--waitretry=10",
            "--tries=0",
            "-O", str(target),
            url,
        ],
        stderr=subprocess.PIPE,
        text=True,
    )
    with _lock:
        _children.append(proc)

    def _pipe_stderr() -> None:
        for line in proc.stderr:
            sys.stderr.write(line)
            sys.stderr.flush()
            stripped = line.strip()
            if total_holder[0] == 0 and stripped.startswith("Length:"):
                try:
                    raw = stripped.split()[1].replace(",", "")
                    if raw.isdigit():
                        total_holder[0] = int(raw)
                        log(f"  Parsed total size from wget: {total_holder[0] / (1024**3):.1f} GB")
                except (IndexError, ValueError):
                    pass

    stderr_thread = threading.Thread(target=_pipe_stderr, daemon=True)
    stderr_thread.start()

    rc = proc.wait()
    stderr_thread.join(timeout=5)

    stop_event.set()
    monitor.join(timeout=5)

    if rc != 0:
        write_status("error", message=f"wget exited with code {rc}")
        raise RuntimeError(f"Download failed (exit code {rc}). Re-run the container to resume.")

    size_gb = target.stat().st_size / (1024**3)
    log(f"Download complete: {target} ({size_gb:.1f} GB)")
    _make_world_writable(target)


# ── Low-zoom extraction ─────────────────────────────────────────────

def extract_lowzoom(scope_dir: Path, date_str: str, source_url: str = "") -> None:
    """Run pmtiles extract to create a small low-zoom file for this version.

    When source_url is provided (area-bbox mode), extracts from the remote
    URL directly (no bbox, global coverage, but only low zoom levels).
    Otherwise extracts from the local versioned tile file.
    """
    lz = lowzoom_file(scope_dir, date_str)
    lz_done = lowzoom_done_file(scope_dir, date_str)

    if lz_done.exists() and lz.exists():
        size_mb = lz.stat().st_size / (1024**2)
        log(f"Low-zoom file already complete: {lz} ({size_mb:.0f} MB) — skipping extraction")
        return

    # Sentinel missing or file absent — (re-)extract.
    if lz.exists():
        size_mb = lz.stat().st_size / (1024**2)
        log(f"Low-zoom sentinel missing — removing incomplete file ({size_mb:.1f} MB) and re-extracting")
        lz.unlink()
    if lz_done.exists():
        lz_done.unlink()

    source = source_url if source_url else str(tile_file(scope_dir, date_str))
    log(f"Extracting low-zoom tiles (zoom 0-{LOWZOOM_MAXZOOM}) from {source} ...")
    write_status("extracting", message=f"Extracting low-zoom tiles (zoom 0-{LOWZOOM_MAXZOOM})...")

    subprocess.run(
        [
            "pmtiles", "extract",
            source, str(lz),
            f"--maxzoom={LOWZOOM_MAXZOOM}",
        ],
        check=True,
    )

    lz_done.touch()
    _make_world_writable(lz)
    _make_world_writable(lz_done)
    size_mb = lz.stat().st_size / (1024**2)
    log(f"Low-zoom extract complete: {lz} ({size_mb:.0f} MB) — sentinel written")


# ── Signal handling ──────────────────────────────────────────────────

def _shutdown(signum, _frame):
    """Forward termination signal to all child processes."""
    sig_name = signal.Signals(signum).name
    log(f"Received {sig_name}, shutting down children...")
    with _lock:
        for proc in _children:
            try:
                proc.terminate()
            except Exception:
                pass
    sys.exit(0)


# ── Area-bbox extraction ─────────────────────────────────────────────

def extract_area(scope_dir: Path, date_str: str, url: str, bbox: str) -> None:
    """Extract tiles for a specific geographic area from a remote PMTiles URL.

    Uses pmtiles extract with --bbox and --maxzoom=15 (Protomaps data cap).
    This fetches only the relevant byte ranges from the CDN — typically
    100-300 MB for a city-level bbox instead of the full 120 GB planet.
    """
    target = tile_file(scope_dir, date_str)
    done = done_file(scope_dir, date_str)

    if done.exists() and target.exists():
        size_mb = target.stat().st_size / (1024**2)
        log(f"Area extract already complete: {target} ({size_mb:.0f} MB) — skipping")
        check_pmtiles_permissions(target)
        return

    # Sentinel missing or file absent — (re-)extract.
    if target.exists():
        size_mb = target.stat().st_size / (1024**2)
        log(f"Area extract sentinel missing — removing incomplete file ({size_mb:.1f} MB) and re-extracting")
        target.unlink()
    if done.exists():
        done.unlink()

    log(f"Extracting area tiles: bbox={bbox}, maxzoom=15, source={url}")
    write_status("area-extract", message=f"Extracting area tiles (bbox={bbox})...")

    subprocess.run(
        [
            "pmtiles", "extract",
            url, str(target),
            f"--bbox={bbox}",
            "--maxzoom=15",
        ],
        check=True,
    )

    done.touch()
    _make_world_writable(target)
    _make_world_writable(done)
    size_mb = target.stat().st_size / (1024**2)
    log(f"Area extract complete: {target} ({size_mb:.0f} MB) — sentinel written")
    check_pmtiles_permissions(target)


# ── Resolve URL and date for a download ──────────────────────────────

def resolve_url_and_date(scope_dir: Path, incomplete_date: str | None = None) -> tuple[str, str]:
    """Resolve the download URL and build date string.

    If incomplete_date is provided, resumes that specific version.
    Otherwise resolves from MAP_DOWNLOAD_URL (which may be 'latest' or a direct URL).

    Returns (url, date_str).
    """
    if incomplete_date:
        uf = url_file(scope_dir, incomplete_date)
        if uf.exists():
            url = uf.read_text().strip()
            log(f"Resuming incomplete download {incomplete_date} with saved URL: {url}")
            return url, incomplete_date
        # No .url file — reconstruct URL from the date
        url = protomaps_build_url(incomplete_date)
        log(f"Resuming incomplete download {incomplete_date} with reconstructed URL: {url}")
        return url, incomplete_date

    url = MAP_DOWNLOAD_URL
    if url == "latest":
        url = resolve_latest_url()

    ds = parse_date_from_url(url)
    # Save URL for resume safety
    url_file(scope_dir, ds).write_text(url)
    _make_world_writable(url_file(scope_dir, ds))
    log(f"Saved resolved URL to {url_file(scope_dir, ds)}")
    return url, ds


# ── Main ─────────────────────────────────────────────────────────────

def main() -> None:
    os.umask(0)  # ensure all created files/dirs — and child processes — use open permissions

    signal.signal(signal.SIGTERM, _shutdown)
    signal.signal(signal.SIGINT, _shutdown)

    log("════════════════════════════════════════════════════════════")
    log("  PMTiles Map Server (Python orchestrator)")
    log("  Stateless — waiting for kickoff with scope")
    log("════════════════════════════════════════════════════════════")

    write_status("starting", message="Initializing...")

    # ── Step 0: Start nginx (immediately healthy) ────────────────────
    nginx = subprocess.Popen(["nginx", "-g", "daemon off;"])
    with _lock:
        _children.append(nginx)
    log(f"nginx started — container is now healthy on port {PORT}")

    # ── Step 1: Start internal kickoff server ────────────────────────
    kickoff_thread = threading.Thread(target=_start_kickoff_server, daemon=True)
    kickoff_thread.start()

    write_status(
        "waiting-kickoff",
        ready=False,
        message="Waiting for kick-off to start map processing.",
    )

    # ── Background worker thread ─────────────────────────────────────
    def background_work():
        while True:
            try:
                # Block until a kickoff arrives carrying a scope
                scope = _kickoff_queue.get()
                scope_dir = DATA_DIR / scope
                scope_dir.mkdir(parents=True, exist_ok=True)
                _make_world_writable(scope_dir)

                log(f"[{scope}] Processing kickoff for scope: {scope} → {scope_dir}")

                # Seed pre-built tiles if available
                seed_prebuilt(scope)

                # ── Step A: Already ready? ───────────────────────────
                active = find_active_symlink(scope_dir)
                if active:
                    size = tile_file(scope_dir, active).stat().st_size
                    if size > 1_000_000_000:
                        log(f"[{scope}] Tiles ready: planet.pmtiles → {active}.pmtiles ({size / (1024**3):.1f} GB)")
                    else:
                        log(f"[{scope}] Tiles ready: planet.pmtiles → {active}.pmtiles ({size / (1024**2):.0f} MB)")
                    check_pmtiles_permissions(tile_file(scope_dir, active))

                    # Always ensure low-zoom file exists; extract_lowzoom is a no-op if already done.
                    lz_url = ""
                    if MAP_AREA_BBOX:
                        uf = url_file(scope_dir, active)
                        if uf.exists():
                            lz_url = uf.read_text().strip()
                    extract_lowzoom(scope_dir, active, source_url=lz_url)
                    if lowzoom_file(scope_dir, active).exists():
                        check_pmtiles_permissions(lowzoom_file(scope_dir, active))
                    create_symlinks(scope_dir, active)

                    total = size
                    write_status(
                        "ready",
                        ready=True,
                        download_pct=100,
                        download_bytes=total,
                        download_total=total,
                        message="Tile server running",
                    )
                    log(f"[{scope}] Tile server is READY and serving requests")
                    continue

                # ── Step B: Incomplete download to resume? ───────────
                incomplete = find_incomplete_download(scope_dir)
                if incomplete:
                    size_mb = tile_file(scope_dir, incomplete).stat().st_size / (1024**2)
                    log(f"[{scope}] Found incomplete download: {incomplete}.pmtiles ({size_mb:.1f} MB) — resuming")
                    url, ds = resolve_url_and_date(scope_dir, incomplete_date=incomplete)

                    if MAP_AREA_BBOX:
                        extract_area(scope_dir, ds, url, MAP_AREA_BBOX)
                    else:
                        download_planet(scope_dir, ds, url)
                        done_file(scope_dir, ds).touch()
                        _make_world_writable(done_file(scope_dir, ds))
                        log(f"[{scope}] Download complete — sentinel written: {done_file(scope_dir, ds)}")
                        check_pmtiles_permissions(tile_file(scope_dir, ds))

                    # Low-zoom extraction
                    extract_lowzoom(scope_dir, ds, source_url=url if MAP_AREA_BBOX else "")
                    check_pmtiles_permissions(lowzoom_file(scope_dir, ds))

                    # Create symlinks
                    create_symlinks(scope_dir, ds)
                    write_status("ready", ready=True, message="Tile server running")
                    log(f"[{scope}] Tile server is READY and serving requests")
                    continue

                # ── Step C: Need to download? ────────────────────────
                if MAP_DOWNLOAD_URL:
                    url, ds = resolve_url_and_date(scope_dir)

                    if MAP_AREA_BBOX:
                        extract_area(scope_dir, ds, url, MAP_AREA_BBOX)
                    else:
                        if tile_file(scope_dir, ds).exists():
                            size_gb = tile_file(scope_dir, ds).stat().st_size / (1024**3)
                            log(f"[{scope}] Tile file present but sentinel missing — resuming ({size_gb:.1f} GB so far)")
                        download_planet(scope_dir, ds, url)
                        done_file(scope_dir, ds).touch()
                        _make_world_writable(done_file(scope_dir, ds))
                        log(f"[{scope}] Download complete — sentinel written: {done_file(scope_dir, ds)}")
                        check_pmtiles_permissions(tile_file(scope_dir, ds))

                    # Low-zoom extraction
                    extract_lowzoom(scope_dir, ds, source_url=url if MAP_AREA_BBOX else "")
                    check_pmtiles_permissions(lowzoom_file(scope_dir, ds))

                    # Create symlinks
                    create_symlinks(scope_dir, ds)
                    write_status("ready", ready=True, message="Tile server running")
                    log(f"[{scope}] Tile server is READY and serving requests")
                    continue

                # ── Step D: Nothing to do ────────────────────────────
                write_status(
                    "error",
                    message=f"No tiles found in {scope_dir} and no MAP_DOWNLOAD_URL configured.",
                )
                log(f"[{scope}] ERROR: No tiles found in {scope_dir} and no MAP_DOWNLOAD_URL configured")

            except Exception as e:
                write_status("error", message=str(e))
                log(f"ERROR: {e}")
                time.sleep(60)  # wait before crash-loop retry

    worker = threading.Thread(target=background_work, daemon=True)
    worker.start()

    # ── Wait for nginx (foreground — receives Docker signals) ────────
    try:
        rc = nginx.wait()
        log(f"nginx exited with code {rc}")
    except KeyboardInterrupt:
        _shutdown(signal.SIGINT, None)


if __name__ == "__main__":
    main()
