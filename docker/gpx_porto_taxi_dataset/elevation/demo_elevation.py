"""Offline, lazily expanded elevation tiles for freshly created demo GPX files."""
from __future__ import annotations

import json
import io
import lzma
import math
import struct
import sys
import time
from array import array
from collections import OrderedDict
from pathlib import Path

from demo_logging import get_logger
from .numeric_elevation import decode_numeric

log = get_logger("DemoElevation")

RASTER_MAGIC = b"MTLDEM01"
RASTER_VERSION = 1
HEADER_LENGTH = struct.Struct("<I")
MAX_HEADER_BYTES = 1024 * 1024
HEIGHT_OFFSET_METERS = 32768
MAX_CACHED_TILES = 16
INTERPOLATION_VARIATION = 0.20
PIXEL_COORDINATE_EPSILON = 1e-9
DEFAULT_RASTER = Path("/app/demo/elevation/data/porto-elevation-120m.mtldem")


def world_pixel(longitude: float, latitude: float, scale: int) -> tuple[float, float]:
    return ((longitude + 180) / 360 * scale,
            (1 - math.asinh(math.tan(math.radians(latitude))) / math.pi) / 2 * scale)


class ElevationRaster:
    """Keep the raster compressed; cache a bounded number of expanded tiles."""

    def __init__(self, path: Path, cache_size: int = MAX_CACHED_TILES):
        started = time.monotonic()
        self.stream = path.open("rb")
        self.cache: OrderedDict[int, array] = OrderedDict()
        self.cache_size = cache_size
        self.expanded_tiles = 0
        self.expansion_seconds = 0.0
        try:
            if self.stream.read(len(RASTER_MAGIC)) != RASTER_MAGIC:
                raise ValueError("Invalid demo elevation raster signature")
            length_bytes = self.stream.read(HEADER_LENGTH.size)
            if len(length_bytes) != HEADER_LENGTH.size:
                raise ValueError("Truncated demo elevation header")
            length = HEADER_LENGTH.unpack(length_bytes)[0]
            if length > MAX_HEADER_BYTES:
                raise ValueError("Oversized demo elevation header")
            self.metadata = json.loads(self.stream.read(length))
            if self.metadata["version"] != RASTER_VERSION:
                raise ValueError("Unsupported demo elevation raster version")
            self.payload_start = self.stream.tell()
            self.tile_size = self.metadata["tile_size"]
            self.scale = 2 ** self.metadata["zoom"] * self.tile_size
            self.tiles = self.metadata["tiles"]
            self.payloads = self.metadata["payloads"]
            self.bounds = self.metadata["bounds"]
            if cache_size < 1 or self.tile_size < 1:
                raise ValueError("Invalid raster tile or cache size")
        except Exception as exception:
            log.error("Demo elevation raster open failed: path=%s, type=%s, message=%s.",
                      path, type(exception).__name__, exception)
            self.stream.close()
            raise
        log.info("Demo elevation raster opened: bytes=%s, zoom=%s, tile size=%s, "
                 "cache limit=%s tiles, duration=%.3fs.", path.stat().st_size,
                 self.metadata["zoom"], self.tile_size, cache_size, time.monotonic() - started)

    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.close()

    def close(self):
        self.cache.clear()
        self.stream.close()

    def _tile(self, index: int) -> array:
        cached = self.cache.get(index)
        if cached is not None:
            self.cache.move_to_end(index)
            return cached
        started = time.monotonic()
        offset, length, codec = self.payloads[index]
        self.stream.seek(self.payload_start + offset)
        compressed = self.stream.read(length)
        if codec == "webp":
            from PIL import Image
            with Image.open(io.BytesIO(compressed)) as image:
                if image.size != (self.tile_size, self.tile_size):
                    raise ValueError(f"Invalid elevation tile dimensions: {index}")
                raw = encoded_heights(image)
        elif codec == "xz":
            raw = lzma.decompress(compressed)
        elif codec.startswith("xz-"):
            values = decode_numeric(lzma.decompress(compressed), self.tile_size, codec.removeprefix("xz-"))
            raw = None
        else:
            raise ValueError(f"Unsupported elevation tile codec: {codec}")
        if raw is not None:
            values = array("H")
            values.frombytes(raw)
            if sys.byteorder != "little":
                values.byteswap()
        if len(values) != self.tile_size * self.tile_size:
            raise ValueError(f"Invalid expanded elevation tile {index}")
        self.cache[index] = values
        if len(self.cache) > self.cache_size:
            self.cache.popitem(last=False)
        self.expanded_tiles += 1
        self.expansion_seconds += time.monotonic() - started
        return values

    def _height(self, x: int, y: int) -> int:
        tile_x, pixel_x = divmod(x, self.tile_size)
        tile_y, pixel_y = divmod(y, self.tile_size)
        index = self.tiles.get(f"{tile_x}/{tile_y}")
        if index is None:
            raise ValueError(f"Elevation tile outside prepared raster: {tile_x}/{tile_y}")
        if index < 0:
            return 0  # Ocean tiles are omitted from Mapterhorn's archive.
        return self._tile(index)[pixel_y * self.tile_size + pixel_x] - HEIGHT_OFFSET_METERS

    def elevation(self, latitude: float, longitude: float, rng=None) -> float:
        if not math.isfinite(latitude) or not math.isfinite(longitude):
            raise ValueError("Non-finite demo track coordinate")
        west, south, east, north = self.bounds
        if not west <= longitude <= east or not south <= latitude <= north:
            raise ValueError(f"Track point outside demo elevation coverage: {latitude},{longitude}")
        # Samples are at pixel centres; subtract half a pixel before interpolation.
        px, py = world_pixel(longitude, latitude, self.scale)
        px -= 0.5
        py -= 0.5
        x, y = math.floor(px + PIXEL_COORDINATE_EPSILON), math.floor(py + PIXEL_COORDINATE_EPSILON)
        fx, fy = max(0.0, px - x), max(0.0, py - y)
        weights = [(1 - fx) * (1 - fy), fx * (1 - fy), (1 - fx) * fy, fx * fy]
        if rng is not None:
            weights = [weight * rng.uniform(1 - INTERPOLATION_VARIATION,
                                            1 + INTERPOLATION_VARIATION) for weight in weights]
        heights = [self._height(x, y), self._height(x + 1, y),
                   self._height(x, y + 1), self._height(x + 1, y + 1)]
        return sum(height * weight for height, weight in zip(heights, weights)) / sum(weights)


def encoded_heights(image) -> bytes:
    """Little-endian unsigned whole-metre heights, offset by +32768."""
    from PIL import ImageMath
    image = image.convert("RGB")
    heights = ImageMath.unsafe_eval('convert(r * 256 + g, "I")',
                                   r=image.getchannel("R"), g=image.getchannel("G"))
    return heights.convert("I;16").tobytes("raw", "I;16")
