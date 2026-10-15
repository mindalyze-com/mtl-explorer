"""Quantize Mapterhorn tiles to whole metres and compress the offline demo raster."""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import lzma
import shutil
import sys
import time
from array import array
from pathlib import Path

from PIL import Image

from .demo_elevation import HEADER_LENGTH, RASTER_MAGIC, RASTER_VERSION, encoded_heights
from demo_logging import get_logger
from .numeric_elevation import encode_numeric

log = get_logger("BuildDemoElevation")

COMPRESSION_PRESET = 9 | lzma.PRESET_EXTREME
COMPRESSION_DICTIONARY_BYTES = 1024 * 1024
ELEVATION_SAMPLE_BYTES = 2


def encode_tile(path: Path, tile_size: int) -> tuple[bytes, bytes]:
    with Image.open(path) as image:
        if image.size != (tile_size, tile_size):
            raise ValueError(f"Unexpected terrain tile dimensions: {path.name}, {image.size}")
        image = image.convert("RGB")
        raw = encoded_heights(image)
        quantized = Image.merge("RGB", (image.getchannel("R"), image.getchannel("G"),
                                         Image.new("L", image.size)))
        optimized = io.BytesIO()
        quantized.save(optimized, format="WEBP", lossless=True, quality=100, method=6, exact=True)
        return raw, optimized.getvalue()


def compress_tile(raw: bytes, predictor: bool = True) -> bytes:
    filters = []
    if predictor:
        filters.append({"id": lzma.FILTER_DELTA, "dist": ELEVATION_SAMPLE_BYTES})
    filters.append({"id": lzma.FILTER_LZMA2, "preset": COMPRESSION_PRESET,
                    "dict_size": COMPRESSION_DICTIONARY_BYTES})
    return lzma.compress(raw, filters=filters)


def build_raster(source: Path, output: Path, encoding: str = "webp") -> dict:
    started = time.monotonic()
    manifest = json.loads((source / "manifest.json").read_text())
    log.info("Preparing demo elevation raster: zoom=%s, coverage factor=%s, tiles=%s.",
             manifest["zoom"], manifest["coverage_factor"], len(manifest["tiles"]))
    chunks, payloads, tiles, fingerprints = [], [], {}, {}
    converted = {}
    raw_bytes = 0
    codecs = {}
    offset = 0
    for tile, filename in sorted(manifest["tiles"].items()):
        if filename is None:
            tiles[tile] = -1
            continue
        if filename not in converted:
            path = source / filename
            if hashlib.sha256(path.read_bytes()).hexdigest() != manifest["hashes"][filename]:
                raise ValueError(f"Terrain tile checksum mismatch: {filename}")
            if encoding == "numeric":
                with Image.open(path) as image:
                    if image.size != (manifest["tile_size"], manifest["tile_size"]):
                        raise ValueError(f"Unexpected terrain tile dimensions: {filename}, {image.size}")
                    raw = encoded_heights(image)
                optimized_webp = None
            else:
                raw, optimized_webp = encode_tile(path, manifest["tile_size"])
            digest = hashlib.sha256(raw).digest()
            if digest not in fingerprints:
                candidates = []
                if encoding != "numeric":
                    candidates.extend([(path.read_bytes(), "webp"), (optimized_webp, "webp")])
                if encoding != "webp":
                    candidates.extend([(compress_tile(raw), "xz"), (compress_tile(raw, predictor=False), "xz")])
                    values = array("H")
                    values.frombytes(raw)
                    if sys.byteorder != "little":
                        values.byteswap()
                    for predictor in ("gradient", "median", "left"):
                        residuals = encode_numeric(values, manifest["tile_size"], predictor)
                        candidates.append((compress_tile(residuals, predictor=False), f"xz-{predictor}"))
                compressed, codec = min(candidates, key=lambda item: len(item[0]))
                index = len(chunks)
                fingerprints[digest] = index
                chunks.append(compressed)
                payloads.append([offset, len(compressed), codec])
                codecs[codec] = codecs.get(codec, 0) + 1
                offset += len(compressed)
                raw_bytes += len(raw)
                if len(chunks) % 20 == 0:
                    log.info("Raster compression progress: unique tiles=%s, payload bytes=%s.", len(chunks), offset)
            converted[filename] = fingerprints[digest]
        tiles[tile] = converted[filename]
    metadata = {"version": RASTER_VERSION, "source": manifest["source"],
                "source_etag": manifest["source_etag"], "attribution": manifest["attribution"],
                "zoom": manifest["zoom"], "tile_size": manifest["tile_size"],
                "track_bounds": manifest["track_bounds"], "bounds": manifest["bounds"],
                "coverage_factor": manifest["coverage_factor"], "height_quantization_m": 1,
                "tiles": tiles, "payloads": payloads}
    header = json.dumps(metadata, separators=(",", ":")).encode()
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_suffix(output.suffix + ".part")
    with temporary.open("wb") as stream:
        stream.write(RASTER_MAGIC + HEADER_LENGTH.pack(len(header)) + header)
        for chunk in chunks:
            stream.write(chunk)
    temporary.replace(output)
    shutil.copyfile(source / "attribution.json", output.parent / "elevation-attribution.json")
    result = {"source_webp_bytes": manifest["source_bytes"], "expanded_unique_bytes": raw_bytes,
              "raster_bytes": output.stat().st_size, "unique_tiles": len(chunks), "codecs": codecs,
              "duration_seconds": time.monotonic() - started,
              "sha256": hashlib.sha256(output.read_bytes()).hexdigest()}
    log.info("Demo raster completed: %s.", result)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--encoding", choices=("numeric", "webp", "smallest"), default="webp")
    args = parser.parse_args()
    try:
        result = build_raster(args.source, args.output, args.encoding)
        args.output.with_suffix(".json").write_text(json.dumps(result, indent=2) + "\n")
    except Exception as exception:
        log.error("Demo elevation preparation failed: %s: %s.", type(exception).__name__, exception, exc_info=True)
        raise


if __name__ == "__main__":
    main()
