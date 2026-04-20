#!/usr/bin/env python3
"""
generate_demo_photos.py — Create geo-tagged placeholder JPEG photos from GPX tracks.

Usage:
    python3 generate_demo_photos.py <gpx_dir> <media_output_dir> <num_photos>

- Scans <gpx_dir> for *.gpx files.
- Picks random track points, applies a small random offset (0–25 m).
- Generates simple placeholder JPEGs with embedded EXIF GPS + DateTimeOriginal.
- Writes to <media_output_dir>/demo-photos/
- **Resumable**: If the output folder already contains N photos, only generates
  (num_photos - N) more.  Safe across Docker restarts.

Dependencies (available in the Docker image):
    pip install Pillow piexif
"""

import glob
import math
import os
import random
import struct
import sys
import xml.etree.ElementTree as ET
from datetime import datetime, timedelta
from pathlib import Path

# ---------------------------------------------------------------------------
# Try importing optional libraries; give a clear error if missing
# ---------------------------------------------------------------------------
try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:
    sys.exit("ERROR: Pillow is required.  pip install Pillow")

try:
    import piexif
except ImportError:
    sys.exit("ERROR: piexif is required.  pip install piexif")


# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------
PHOTO_WIDTH = 1024
PHOTO_HEIGHT = 768
MAX_OFFSET_METERS = 25  # random position jitter around the track point
EARTH_RADIUS_M = 6_371_000

# Porto-area colour palette — gentle watercolour-style backgrounds
PALETTES = [
    ((45, 80, 120), (180, 210, 235)),   # blue dusk
    ((60, 100, 60), (180, 220, 170)),    # green park
    ((130, 80, 50), (240, 210, 180)),    # warm terracotta
    ((80, 60, 100), (200, 180, 220)),    # lavender twilight
    ((30, 70, 90), (160, 200, 210)),     # teal harbour
    ((100, 50, 30), (230, 190, 150)),    # sandstone
]


# ---------------------------------------------------------------------------
# GPX parsing helpers
# ---------------------------------------------------------------------------
def parse_gpx_trackpoints(gpx_path):
    """Return list of (lat, lon, datetime) from a GPX file."""
    points = []
    try:
        tree = ET.parse(gpx_path)
    except ET.ParseError:
        return points

    root = tree.getroot()
    # Handle GPX namespace
    ns = ''
    if root.tag.startswith('{'):
        ns = root.tag.split('}')[0] + '}'

    for trkpt in root.iter(f'{ns}trkpt'):
        lat = trkpt.get('lat')
        lon = trkpt.get('lon')
        time_el = trkpt.find(f'{ns}time')
        if lat and lon and time_el is not None and time_el.text:
            try:
                t = datetime.strptime(time_el.text.rstrip('Z'), '%Y-%m-%dT%H:%M:%S')
            except ValueError:
                continue
            points.append((float(lat), float(lon), t))
    return points


def collect_all_trackpoints(gpx_dir):
    """Scan all .gpx files and return a flat list of (lat, lon, datetime)."""
    all_points = []
    gpx_files = sorted(glob.glob(os.path.join(gpx_dir, '**', '*.gpx'), recursive=True))
    for f in gpx_files:
        all_points.extend(parse_gpx_trackpoints(f))
    return all_points


# ---------------------------------------------------------------------------
# Geo helpers
# ---------------------------------------------------------------------------
def offset_point(lat, lon, max_meters):
    """Apply a random offset of 0–max_meters in a random bearing."""
    distance = random.uniform(0, max_meters)
    bearing = random.uniform(0, 2 * math.pi)

    d_lat = (distance * math.cos(bearing)) / EARTH_RADIUS_M
    d_lon = (distance * math.sin(bearing)) / (EARTH_RADIUS_M * math.cos(math.radians(lat)))

    return lat + math.degrees(d_lat), lon + math.degrees(d_lon)


# ---------------------------------------------------------------------------
# EXIF helpers
# ---------------------------------------------------------------------------
def _to_deg_min_sec(decimal_deg):
    """Convert decimal degrees to (degrees, minutes, seconds) as rationals for EXIF."""
    d = int(abs(decimal_deg))
    m_float = (abs(decimal_deg) - d) * 60
    m = int(m_float)
    s = round((m_float - m) * 60 * 10000)  # store with 4-decimal precision
    return ((d, 1), (m, 1), (s, 10000))


def build_exif_bytes(lat, lon, dt):
    """Build EXIF bytes with GPS coordinates and DateTimeOriginal."""
    exif_dict = {"0th": {}, "Exif": {}, "GPS": {}, "1st": {}}

    # DateTimeOriginal
    dt_str = dt.strftime('%Y:%m:%d %H:%M:%S')
    exif_dict["Exif"][piexif.ExifIFD.DateTimeOriginal] = dt_str.encode()
    exif_dict["0th"][piexif.ImageIFD.DateTime] = dt_str.encode()

    # GPS
    exif_dict["GPS"][piexif.GPSIFD.GPSLatitudeRef] = b'N' if lat >= 0 else b'S'
    exif_dict["GPS"][piexif.GPSIFD.GPSLatitude] = _to_deg_min_sec(lat)
    exif_dict["GPS"][piexif.GPSIFD.GPSLongitudeRef] = b'E' if lon >= 0 else b'W'
    exif_dict["GPS"][piexif.GPSIFD.GPSLongitude] = _to_deg_min_sec(lon)

    return piexif.dump(exif_dict)


# ---------------------------------------------------------------------------
# Image generation
# ---------------------------------------------------------------------------
def generate_placeholder_jpeg(lat, lon, dt, output_path):
    """Create a simple placeholder JPEG with gradient background and text overlay."""
    dark, light = random.choice(PALETTES)

    img = Image.new('RGB', (PHOTO_WIDTH, PHOTO_HEIGHT))
    draw = ImageDraw.Draw(img)

    # Gradient fill
    for y in range(PHOTO_HEIGHT):
        ratio = y / PHOTO_HEIGHT
        r = int(dark[0] + (light[0] - dark[0]) * ratio)
        g = int(dark[1] + (light[1] - dark[1]) * ratio)
        b = int(dark[2] + (light[2] - dark[2]) * ratio)
        draw.line([(0, y), (PHOTO_WIDTH, y)], fill=(r, g, b))

    # Add some subtle "bokeh" circles for visual interest
    for _ in range(random.randint(8, 20)):
        cx = random.randint(0, PHOTO_WIDTH)
        cy = random.randint(0, PHOTO_HEIGHT)
        radius = random.randint(20, 80)
        alpha_circle = random.randint(15, 50)
        overlay = Image.new('RGBA', (PHOTO_WIDTH, PHOTO_HEIGHT), (0, 0, 0, 0))
        overlay_draw = ImageDraw.Draw(overlay)
        overlay_draw.ellipse(
            [cx - radius, cy - radius, cx + radius, cy + radius],
            fill=(255, 255, 255, alpha_circle)
        )
        img = Image.alpha_composite(img.convert('RGBA'), overlay).convert('RGB')

    # Text label
    draw = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 18)
        font_small = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 14)
    except (IOError, OSError):
        font = ImageFont.load_default()
        font_small = font

    text_lines = [
        f"MyTrailLog Demo",
        f"{dt.strftime('%Y-%m-%d %H:%M')}",
        f"{lat:.5f}°, {lon:.5f}°",
    ]

    y_offset = PHOTO_HEIGHT - 90
    for i, line in enumerate(text_lines):
        f = font if i == 0 else font_small
        # Shadow
        draw.text((22, y_offset + 2), line, font=f, fill=(0, 0, 0, 180))
        # Main text
        draw.text((20, y_offset), line, font=f, fill=(255, 255, 255))
        y_offset += 24

    # Save with EXIF
    exif_bytes = build_exif_bytes(lat, lon, dt)
    img.save(output_path, 'JPEG', quality=82, exif=exif_bytes)


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
def main():
    if len(sys.argv) != 4:
        print(f"Usage: {sys.argv[0]} <gpx_dir> <media_output_dir> <num_photos>")
        sys.exit(1)

    gpx_dir = sys.argv[1]
    media_output_dir = sys.argv[2]
    num_photos = int(sys.argv[3])

    output_dir = os.path.join(media_output_dir, 'demo-photos')
    os.makedirs(output_dir, exist_ok=True)

    # --- Resumability: count existing photos ---
    existing = set(glob.glob(os.path.join(output_dir, 'demo_photo_*.jpg')))
    existing_count = len(existing)

    if existing_count >= num_photos:
        print(f"✅ Demo photos: {existing_count}/{num_photos} already exist — nothing to do.")
        return

    remaining = num_photos - existing_count
    print(f"📷 Demo photos: {existing_count} already exist, generating {remaining} more (target: {num_photos})…")

    # --- Collect track points ---
    all_points = collect_all_trackpoints(gpx_dir)
    if not all_points:
        print("⚠️  No track points found in GPX files — cannot generate photos.")
        sys.exit(1)

    print(f"   Found {len(all_points)} track points across GPX files.")

    # --- Determine which indices are already taken ---
    existing_indices = set()
    for path in existing:
        basename = os.path.basename(path)
        try:
            idx = int(basename.replace('demo_photo_', '').replace('.jpg', ''))
            existing_indices.add(idx)
        except ValueError:
            pass

    # --- Generate photos ---
    generated = 0
    next_index = 1
    while generated < remaining:
        # Find next free index
        while next_index in existing_indices:
            next_index += 1

        # Pick a random track point
        lat, lon, dt = random.choice(all_points)

        # Apply small random offset (0–25 m)
        lat_offset, lon_offset = offset_point(lat, lon, MAX_OFFSET_METERS)

        # Add small random time shift (±0–30 minutes) so photos aren't at exact track-point second
        time_jitter = timedelta(minutes=random.uniform(-30, 30))
        dt_photo = dt + time_jitter

        filename = f"demo_photo_{next_index:05d}.jpg"
        filepath = os.path.join(output_dir, filename)

        generate_placeholder_jpeg(lat_offset, lon_offset, dt_photo, filepath)

        generated += 1
        next_index += 1

        if generated % 50 == 0 or generated == remaining:
            print(f"   … generated {generated}/{remaining}")

    print(f"✅ Done. Total demo photos now: {existing_count + generated}")


if __name__ == '__main__':
    main()
