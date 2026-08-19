"""Shared placeholder-photo renderer for demo and test data."""

from __future__ import annotations

import random
from datetime import datetime, timezone
from functools import lru_cache
from pathlib import Path

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:
    raise SystemExit("ERROR: Pillow is required. pip install Pillow")

try:
    import piexif
except ImportError:
    raise SystemExit("ERROR: piexif is required. pip install piexif")


PHOTO_WIDTH = 1024
PHOTO_HEIGHT = 768
NEAREST_RESAMPLE = getattr(Image, "Resampling", Image).NEAREST

# Distinct watercolour-style backgrounds used by both demo and regression photos.
PALETTES = (
    ((45, 80, 120), (180, 210, 235)),   # blue dusk
    ((60, 100, 60), (180, 220, 170)),   # green park
    ((130, 80, 50), (240, 210, 180)),   # warm terracotta
    ((80, 60, 100), (200, 180, 220)),   # lavender twilight
    ((30, 70, 90), (160, 200, 210)),    # teal harbour
    ((100, 50, 30), (230, 190, 150)),   # sandstone
    ((105, 35, 65), (250, 175, 190)),   # rose sunrise
    ((115, 75, 15), (250, 220, 105)),   # golden afternoon
    ((20, 65, 145), (105, 205, 240)),   # Atlantic blue
    ((25, 100, 75), (165, 235, 195)),   # mint garden
    ((145, 50, 40), (250, 165, 115)),   # coral sunset
    ((40, 40, 110), (155, 175, 240)),   # indigo evening
    ((115, 35, 105), (235, 155, 220)),  # magenta dusk
    ((75, 85, 25), (215, 220, 135)),    # olive hillside
    ((45, 60, 75), (185, 205, 225)),    # silver rain
    ((145, 60, 10), (255, 185, 75)),    # burnt orange
    ((10, 95, 110), (125, 230, 220)),   # turquoise coast
    ((100, 25, 45), (240, 205, 155)),   # burgundy glow
)

GRADIENT_CACHE_SIZE = len(PALETTES) * 2
FONT_CACHE_SIZE = 8


@lru_cache(maxsize=GRADIENT_CACHE_SIZE)
def _gradient_strip(
    dark: tuple[int, int, int],
    light: tuple[int, int, int],
    height: int,
) -> Image.Image:
    """Cache a one-pixel-wide gradient instead of a full-resolution image."""
    image = Image.new("RGB", (1, height))
    draw = ImageDraw.Draw(image)
    for y in range(height):
        ratio = y / height
        colour = tuple(
            int(dark[channel] + (light[channel] - dark[channel]) * ratio)
            for channel in range(3)
        )
        draw.point((0, y), fill=colour)
    return image


@lru_cache(maxsize=FONT_CACHE_SIZE)
def _fonts(
    title_font_size: int,
    detail_font_size: int,
) -> tuple[ImageFont.ImageFont, ImageFont.ImageFont]:
    """Load the two shared fonts once per process and rendered size."""
    try:
        return (
            ImageFont.truetype(
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                title_font_size,
            ),
            ImageFont.truetype(
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                detail_font_size,
            ),
        )
    except (IOError, OSError):
        fallback = ImageFont.load_default()
        return fallback, fallback


def _composite_ellipse(
    image: Image.Image,
    center_x: int,
    center_y: int,
    radius: int,
    alpha: int,
) -> None:
    """Composite one clipped ellipse without allocating a full-image overlay."""
    diameter = radius * 2
    patch = Image.new("RGBA", (diameter + 1, diameter + 1), (0, 0, 0, 0))
    ImageDraw.Draw(patch).ellipse(
        (0, 0, diameter, diameter),
        fill=(255, 255, 255, alpha),
    )

    destination_x = center_x - radius
    destination_y = center_y - radius
    source_left = max(0, -destination_x)
    source_top = max(0, -destination_y)
    source_right = min(patch.width, image.width - destination_x)
    source_bottom = min(patch.height, image.height - destination_y)
    if source_left >= source_right or source_top >= source_bottom:
        return

    visible_patch = patch.crop((source_left, source_top, source_right, source_bottom))
    image.alpha_composite(
        visible_patch,
        dest=(destination_x + source_left, destination_y + source_top),
    )


def _to_deg_min_sec(decimal_deg: float) -> tuple[tuple[int, int], ...]:
    """Convert decimal degrees to EXIF degree/minute/second rationals."""
    degrees = int(abs(decimal_deg))
    minute_value = (abs(decimal_deg) - degrees) * 60
    minutes = int(minute_value)
    seconds = round((minute_value - minutes) * 60 * 10_000)
    return ((degrees, 1), (minutes, 1), (seconds, 10_000))


def build_exif_bytes(
    lat: float,
    lon: float,
    captured_at: datetime,
    *,
    include_gps: bool = True,
    include_gps_timestamp: bool = False,
) -> bytes:
    """Build EXIF bytes with capture time and optional GPS coordinates/time."""
    exif_dict = {"0th": {}, "Exif": {}, "GPS": {}, "1st": {}}
    datetime_value = captured_at.strftime("%Y:%m:%d %H:%M:%S").encode()

    exif_dict["Exif"][piexif.ExifIFD.DateTimeOriginal] = datetime_value
    exif_dict["0th"][piexif.ImageIFD.DateTime] = datetime_value
    if include_gps:
        exif_dict["GPS"][piexif.GPSIFD.GPSLatitudeRef] = b"N" if lat >= 0 else b"S"
        exif_dict["GPS"][piexif.GPSIFD.GPSLatitude] = _to_deg_min_sec(lat)
        exif_dict["GPS"][piexif.GPSIFD.GPSLongitudeRef] = b"E" if lon >= 0 else b"W"
        exif_dict["GPS"][piexif.GPSIFD.GPSLongitude] = _to_deg_min_sec(lon)
        if include_gps_timestamp:
            gps_datetime = (
                captured_at.replace(tzinfo=timezone.utc)
                if captured_at.tzinfo is None
                else captured_at.astimezone(timezone.utc)
            )
            gps_seconds = gps_datetime.second * 1_000_000 + gps_datetime.microsecond
            exif_dict["GPS"][piexif.GPSIFD.GPSDateStamp] = gps_datetime.strftime("%Y:%m:%d").encode()
            exif_dict["GPS"][piexif.GPSIFD.GPSTimeStamp] = (
                (gps_datetime.hour, 1),
                (gps_datetime.minute, 1),
                (gps_seconds, 1_000_000),
            )
    return piexif.dump(exif_dict)


def generate_placeholder_jpeg(
    lat: float,
    lon: float,
    captured_at: datetime,
    output_path: str | Path,
    *,
    title: str = "MTL Explorer Demo",
    palette: tuple[tuple[int, int, int], tuple[int, int, int]] | None = None,
    rng: random.Random | None = None,
    include_gps: bool = True,
    include_gps_timestamp: bool = False,
    width: int = PHOTO_WIDTH,
    height: int = PHOTO_HEIGHT,
    jpeg_quality: int = 82,
) -> None:
    """Create a labelled gradient JPEG with capture-time EXIF and optional GPS."""
    random_source = rng or random
    dark, light = palette or random_source.choice(PALETTES)

    image = _gradient_strip(dark, light, height).resize(
        (width, height),
        resample=NEAREST_RESAMPLE,
    ).convert("RGBA")

    for _ in range(random_source.randint(8, 20)):
        center_x = random_source.randint(0, width)
        center_y = random_source.randint(0, height)
        radius = random_source.randint(max(4, height // 38), max(8, height // 10))
        alpha = random_source.randint(15, 50)
        _composite_ellipse(image, center_x, center_y, radius, alpha)

    image = image.convert("RGB")
    draw = ImageDraw.Draw(image)
    title_font_size = max(12, round(height * 0.024))
    detail_font_size = max(10, round(height * 0.018))
    font, small_font = _fonts(title_font_size, detail_font_size)

    text_lines = (
        title,
        captured_at.strftime("%Y-%m-%d %H:%M"),
        f"{lat:.5f}°, {lon:.5f}°",
    )
    line_height = max(16, round(height * 0.031))
    x_offset = max(8, round(width * 0.02))
    y_offset = height - line_height * len(text_lines) - max(8, round(height * 0.016))
    for index, line in enumerate(text_lines):
        selected_font = font if index == 0 else small_font
        draw.text((x_offset + 2, y_offset + 2), line, font=selected_font, fill=(0, 0, 0, 180))
        draw.text((x_offset, y_offset), line, font=selected_font, fill=(255, 255, 255))
        y_offset += line_height

    exif_bytes = build_exif_bytes(
        lat,
        lon,
        captured_at,
        include_gps=include_gps,
        include_gps_timestamp=include_gps_timestamp,
    )
    image.save(output_path, "JPEG", quality=jpeg_quality, exif=exif_bytes)
