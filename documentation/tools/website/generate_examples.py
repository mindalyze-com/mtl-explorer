#!/usr/bin/env python3
"""Generate a fictional GPX archive offline."""

from datetime import datetime, timedelta, timezone
from html import escape
import math
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[3]
OUTPUT = ROOT / 'docs' / 'examples' / 'synthetic-archive'
POINT_COUNT = 181
TRACK_COUNT = 12
YEAR_SPLIT = 8
SECONDS_PER_POINT = 12
LONGITUDE_SPAN = 0.036
LATITUDE_SPAN = 0.024


def coordinates(index):
    """Three mathematical loops near 0,0; no recorded journey or road data."""
    branch = index % 3 if index < YEAR_SPLIT else (index - YEAR_SPLIT) % 2
    for point in range(POINT_COUNT):
        angle = 2 * math.pi * point / (POINT_COUNT - 1)
        # Small variations make each journey distinct while preserving shared paths.
        x = .5 + (.32 + branch * .045) * math.cos(angle)
        y = .5 + .27 * math.sin(angle) + branch * .065 * math.sin(2 * angle)
        x += index * .0005 * math.sin(3 * angle)
        y += index * .0005 * math.sin(5 * angle)
        yield x, y


def gpx(index, points):
    year = 2024 if index < YEAR_SPLIT else 2025
    start = datetime(year, 6, index + 1, 9, tzinfo=timezone.utc)
    name = f'Synthetic loop {index + 1:02d} ({year})'
    lines = ['<?xml version="1.0" encoding="UTF-8"?>',
             '<gpx version="1.1" creator="MTL Explorer synthetic example" xmlns="http://www.topografix.com/GPX/1/1">',
             '<metadata><desc>Fictional mathematical coordinates near 0,0. No recorded activity. Do not navigate this route.</desc></metadata>',
             f'<trk><name>{escape(name)}</name><trkseg>']
    for point, (x, y) in enumerate(points):
        time = (start + timedelta(seconds=point * SECONDS_PER_POINT)).isoformat().replace('+00:00', 'Z')
        elevation = 100 + 15 * math.sin(2 * math.pi * point / (POINT_COUNT - 1))
        lines.append(f'<trkpt lat="{(y-.5)*LATITUDE_SPAN:.7f}" lon="{(x-.5)*LONGITUDE_SPAN:.7f}"><ele>{elevation:.2f}</ele><time>{time}</time></trkpt>')
    return '\n'.join(lines + ['</trkseg></trk>', '</gpx>', ''])


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    tracks = [list(coordinates(i)) for i in range(TRACK_COUNT)]
    with zipfile.ZipFile(OUTPUT/'synthetic-tracks.zip', 'w', compression=zipfile.ZIP_DEFLATED) as archive:
        for index, points in enumerate(tracks):
            entry = zipfile.ZipInfo(f'synthetic-loop-{index+1:02d}.gpx', date_time=(2026, 9, 5, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(entry, gpx(index, points))
    print(f'Generated {TRACK_COUNT} synthetic GPX files in {OUTPUT}')


if __name__ == '__main__':
    main()
