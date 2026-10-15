"""Build and validate compact metadata for the packaged public demo GPX archive."""
from __future__ import annotations

import gzip
import hashlib
import json
import sys
import time
import zipfile
import zlib
from datetime import datetime
from pathlib import Path, PurePosixPath
from demo_logging import get_logger

log = get_logger("DemoTrackCatalog")

CATALOG_VERSION = 1
READ_CHUNK_BYTES = 64 * 1024
GENERATED_CATALOG_NAME = ".demo-track-catalog.json.gz"


def sha256(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def archive_manifest(archive: zipfile.ZipFile) -> list[dict]:
    return [{"name": member.filename, "size": member.file_size, "crc32": member.CRC}
            for member in sorted(archive.infolist(), key=lambda member: member.filename)
            if not member.is_dir() and member.filename.endswith(".gpx")]


def build_catalog(zip_path: Path, output_path: Path) -> None:
    from generate_demo_photos import parse_gpx_track
    started = time.monotonic()
    log.info("Building demo track catalog from %s.", zip_path.name)
    tracks = []
    with zipfile.ZipFile(zip_path) as archive:
        files = archive_manifest(archive)
        for file in files:
            # glob("**/*.gpx") used by collect_tracks omits hidden files/directories.
            if any(part.startswith(".") for part in PurePosixPath(file["name"]).parts):
                continue
            with archive.open(file["name"]) as stream:
                track = parse_gpx_track(file["name"], stream)
            if track is not None:
                tracks.append({"name": file["name"], "timed_segment_count": track.timed_segment_count,
                               "start": track.start.isoformat(), "end": track.end.isoformat()})
    catalog = {"version": CATALOG_VERSION, "zip_sha256": sha256(zip_path),
               "files": files, "tracks": tracks}
    output_path.write_bytes(gzip.compress(json.dumps(catalog, separators=(",", ":")).encode(), mtime=0))
    log.info("Demo track catalog built: tracks=%s, files=%s, bytes=%s, duration=%.2fs.",
             len(tracks), len(files), output_path.stat().st_size, time.monotonic() - started)


def load_tracks(gpx_dir: Path, catalog_path: Path, zip_path: Path):
    from generate_demo_photos import DemoTrack, collect_tracks
    started = time.monotonic()
    log.info("Validating demo track catalog, ZIP hash and extracted GPX names, sizes and CRC32.")
    try:
        generated_catalog = gpx_dir / GENERATED_CATALOG_NAME
        if generated_catalog.exists():
            catalog_path = generated_catalog
        catalog = json.loads(gzip.decompress(catalog_path.read_bytes()))
        if catalog["version"] != CATALOG_VERSION or catalog["zip_sha256"] != sha256(zip_path):
            raise ValueError("catalog version or ZIP hash differs")
        with zipfile.ZipFile(zip_path) as archive:
            if catalog["files"] != archive_manifest(archive):
                raise ValueError("catalog ZIP manifest differs")
        generated_files = catalog.get("extracted_files", catalog["files"])
        if [file["name"] for file in generated_files] != [file["name"] for file in catalog["files"]]:
            raise ValueError("generated catalog filenames differ from source ZIP")
        extracted = sorted(path.relative_to(gpx_dir).as_posix() for path in gpx_dir.rglob("*.gpx"))
        if extracted != [file["name"] for file in generated_files]:
            raise ValueError("extracted GPX filenames differ (stale or partial extraction)")
        for file in generated_files:
            path = gpx_dir / file["name"]
            if path.stat().st_size != file["size"]:
                raise ValueError(f"extracted GPX size differs: {file['name']}")
            checksum = 0
            with path.open("rb") as stream:
                while chunk := stream.read(READ_CHUNK_BYTES):
                    checksum = zlib.crc32(chunk, checksum)
            if checksum != file["crc32"]:
                raise ValueError(f"extracted GPX CRC32 differs: {file['name']}")
        names = [entry["name"] for entry in catalog["tracks"]]
        if names != sorted(set(names)) or not set(names).issubset(extracted):
            raise ValueError("catalog track order or names differ")
        tracks = [DemoTrack(gpx_dir / entry["name"], entry["timed_segment_count"],
                            datetime.fromisoformat(entry["start"]), datetime.fromisoformat(entry["end"]))
                  for entry in catalog["tracks"]]
        if any(track.start.tzinfo is not None or track.end.tzinfo is not None
               or track.timed_segment_count < 0 or track.start > track.end for track in tracks):
            raise ValueError("invalid catalog track metadata")
        log.info("Demo track catalog validated: tracks=%s, duration=%.2fs.", len(tracks), time.monotonic() - started)
        return tracks
    except (OSError, ValueError, KeyError, TypeError, EOFError, zipfile.BadZipFile, zlib.error) as exception:
        log.warning("Demo track catalog unavailable: %s: %s; parsing extracted GPX.", type(exception).__name__, exception)
        tracks = collect_tracks(gpx_dir)
        log.info("Demo GPX metadata parsed: tracks=%s, duration=%.2fs.", len(tracks), time.monotonic() - started)
        return tracks


if __name__ == "__main__":
    build_catalog(Path(sys.argv[1]), Path(sys.argv[2]))
