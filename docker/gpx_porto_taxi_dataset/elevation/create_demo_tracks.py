"""Create demo GPX files with track descriptions and offline terrain elevation."""
from __future__ import annotations

import argparse
import gzip
import json
import random
import re
import time
import zipfile
import zlib
from contextlib import nullcontext
from html import unescape
from pathlib import Path, PurePosixPath
from xml.sax.saxutils import escape

from .demo_elevation import DEFAULT_RASTER, ElevationRaster
from demo_logging import get_logger
from demo_track_catalog import GENERATED_CATALOG_NAME, archive_manifest, sha256

log = get_logger("DemoTracks")

DEFAULT_CATALOG = Path("/app/demo/demo-track-catalog.json.gz")
GENERATION_MARKER = ".demo-track-generation-in-progress"
TRACK_PROGRESS_INTERVAL = 1000
RANDOM_SEED_PREFIX = "mtl-demo-elevation-v1:"
POINT_PATTERN = re.compile(
    rb'(?P<open><(?P<prefix>(?:[\w.-]+:)?)trkpt\b(?P<attrs>[^>]*)(?<!/)>)(?P<body>.*?)</(?P=prefix)trkpt>',
    re.DOTALL)
LATITUDE_PATTERN = re.compile(rb'\blat\s*=\s*[\"\']([^\"\']+)[\"\']')
LONGITUDE_PATTERN = re.compile(rb'\blon\s*=\s*[\"\']([^\"\']+)[\"\']')
ELEVATION_PATTERN = re.compile(rb'<(?:[\w.-]+:)?ele\b')
DESCRIPTION_PATTERN = re.compile(rb'(<(?:[\w.-]+:)?desc>)(.*?)(</(?:[\w.-]+:)?desc>)', re.DOTALL)
ELEVATION_DESCRIPTION = b" | Elevation: Mapterhorn ~120 m terrain; interpolation weights +/-20% (synthetic)."
TRACK_HEADER_PATTERN = re.compile(
    rb'(?P<open><(?P<prefix>(?:[\w.-]+:)?)trk\b[^>]*>)(?P<header>.*?)'
    rb'(?=<(?P=prefix)(?:src|link|number|type|extensions|trkseg)\b|</(?P=prefix)trk>)', re.DOTALL)
TRIP_ID_PATTERN = re.compile(
    rb'<(?P<prefix>(?:[\w.-]+:)?)name\b[^>]*>\s*Demo Trip\s+(?P<trip_id>.*?)</(?P=prefix)name>',
    re.DOTALL)
PRE_DESCRIPTION_FIELD_PATTERN = re.compile(
    rb'<(?P<prefix>(?:[\w.-]+:)?)(?P<tag>name|cmt)\b[^>]*>.*?</(?P=prefix)(?P=tag)>', re.DOTALL)
DEMO_TRACK_DESCRIPTION = (
    "Demo GPS track from the Porto taxi dataset (trip ID: {trip_id}). "
    "Recording dates were changed, and estimated elevation was added because "
    "the original data contains no elevation measurements. "
    "The demo also includes randomly assigned photos on some tracks and synthetic heart rate data."
)


def inject_track_descriptions(raw: bytes, name: str) -> bytes:
    def replace_header(match):
        header = match["header"]
        identifier = TRIP_ID_PATTERN.search(header)
        trip_id = unescape(identifier["trip_id"].decode()).strip() if identifier is not None else ""
        if not trip_id:
            raise ValueError(f"Demo track missing original Porto trip ID: {name}")
        prefix = match["prefix"]
        description = (b"<" + prefix + b"desc>"
                       + escape(DEMO_TRACK_DESCRIPTION.format(trip_id=trip_id)).encode()
                       + b"</" + prefix + b"desc>")
        existing = DESCRIPTION_PATTERN.search(header)
        if existing is not None:
            header = header[:existing.start()] + description + header[existing.end():]
        else:
            fields = list(PRE_DESCRIPTION_FIELD_PATTERN.finditer(header))
            position = fields[-1].end()
            header = header[:position] + b"\n    " + description + header[position:]
        return match["open"] + header

    result, count = TRACK_HEADER_PATTERN.subn(replace_header, raw)
    if not count:
        raise ValueError(f"Demo GPX missing track: {name}")
    return result


def inject_elevations(raw: bytes, raster: ElevationRaster, name: str) -> tuple[bytes, int]:
    rng = random.Random(RANDOM_SEED_PREFIX + name)
    added = 0

    def replace_point(match):
        nonlocal added
        if ELEVATION_PATTERN.search(match["body"]):
            return match[0]
        latitude = LATITUDE_PATTERN.search(match["attrs"])
        longitude = LONGITUDE_PATTERN.search(match["attrs"])
        if latitude is None or longitude is None:
            raise ValueError(f"Demo track point missing coordinates: {name}")
        height = raster.elevation(float(latitude[1]), float(longitude[1]), rng)
        prefix = match["prefix"]
        added += 1
        return (match["open"] + b"\n        <" + prefix + b"ele>" + f"{height:.1f}".encode()
                + b"</" + prefix + b"ele>" + match["body"] + b"</" + prefix + b"trkpt>")

    result = POINT_PATTERN.sub(replace_point, raw)
    if added:
        result = DESCRIPTION_PATTERN.sub(lambda m: m[1] + m[2] + ELEVATION_DESCRIPTION + m[3],
                                         result, count=1)
    return result, added


def generate_tracks(archive_path: Path, output: Path, raster_path: Path | None = DEFAULT_RASTER,
                    limit: int = 0, catalog_path: Path | None = DEFAULT_CATALOG) -> dict:
    started = time.monotonic()
    output.mkdir(parents=True, exist_ok=True)
    marker = output / GENERATION_MARKER
    marker.write_text("Demo GPX creation in progress; resume on next startup.\n")
    created = resumed = points = 0
    transformed_manifest = []
    log.info("Demo GPX creation starting with per-track descriptions: elevation=%s, track limit=%s, output=%s.",
             raster_path is not None, limit or "all", output)
    with (ElevationRaster(raster_path) if raster_path is not None else nullcontext()) as raster:
        opened = time.monotonic()
        with zipfile.ZipFile(archive_path) as archive:
            files = archive_manifest(archive)
            selected = files[:limit] if limit > 0 else files
            for file in selected:
                name = file["name"]
                relative = PurePosixPath(name)
                if relative.is_absolute() or ".." in relative.parts:
                    raise ValueError(f"Invalid demo archive path: {name}")
                path = output / name
                if path.exists():
                    raw = path.read_bytes()
                    resumed += 1
                else:
                    raw = archive.read(name)
                    if raster is not None:
                        raw, added = inject_elevations(raw, raster, name)
                        points += added
                    raw = inject_track_descriptions(raw, name)
                    path.parent.mkdir(parents=True, exist_ok=True)
                    temporary = path.with_suffix(path.suffix + ".part")
                    temporary.write_bytes(raw)
                    temporary.replace(path)
                    created += 1
                transformed_manifest.append({"name": name, "size": len(raw),
                                             "crc32": zlib.crc32(raw)})
                processed = created + resumed
                if processed % TRACK_PROGRESS_INTERVAL == 0 or processed == len(selected):
                    log.info("Demo GPX progress: files=%s/%s, added elevations=%s, duration=%.3fs.",
                             processed, len(selected), points, time.monotonic() - started)
        creation_seconds = time.monotonic() - opened
        if catalog_path is not None and catalog_path.exists() and len(selected) == len(files):
            catalog = json.loads(gzip.decompress(catalog_path.read_bytes()))
            if catalog["zip_sha256"] != sha256(archive_path) or catalog["files"] != files:
                raise ValueError("Packaged demo track catalog does not match the source archive")
            catalog["extracted_files"] = transformed_manifest
            target = output / GENERATED_CATALOG_NAME
            temporary = target.with_suffix(".part")
            temporary.write_bytes(gzip.compress(json.dumps(catalog, separators=(",", ":")).encode(), mtime=0))
            temporary.replace(target)
            log.info("Demo GPX catalog saved with generated file checksums: tracks=%s.", len(selected))
        result = {"tracks": len(selected), "created": created, "resumed": resumed,
                  "elevation_points": points, "raster_open_seconds": opened - started,
                  "creation_seconds": creation_seconds,
                  "expanded_tiles": raster.expanded_tiles if raster else 0,
                  "tile_expansion_seconds": raster.expansion_seconds if raster else 0,
                  "duration_seconds": time.monotonic() - started}
    marker.unlink()
    log.info("Demo GPX creation completed: %s.", result)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--raster", type=Path, default=DEFAULT_RASTER)
    args = parser.parse_args()
    try:
        generate_tracks(args.archive, args.output, args.raster)
    except Exception as exception:
        log.error("Demo GPX creation failed: %s: %s; completed files are retained for resume.",
                  type(exception).__name__, exception, exc_info=True)
        raise


if __name__ == "__main__":
    main()
