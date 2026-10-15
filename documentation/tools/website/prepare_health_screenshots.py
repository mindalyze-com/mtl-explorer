#!/usr/bin/env python3
"""Encode verified in-app browser captures for the Google Health showcase."""

import argparse
from pathlib import Path

from PIL import Image


ASSETS = Path(__file__).resolve().parents[3] / "docs/assets/feature-showcase"
SHOTS = ("google-health-overview", "google-health-track")
WEBP_QUALITY = 88
VARIANT_WIDTHS = {"thumb": 360, "medium": 720, "preview": 1099, "large": None}


def prepare(captures, output):
    # Read both inputs before replacing any assets. Capture provenance is checked
    # in the browser; this helper only resizes and encodes the original pixels.
    sources = {}
    for shot in SHOTS:
        with Image.open(captures / f"{shot}.jpg") as source:
            sources[shot] = source.convert("RGB")
    for shot, source in sources.items():
        for variant, target in VARIANT_WIDTHS.items():
            width = min(target or source.width, source.width)
            height = round(source.height * width / source.width)
            resized = source.resize((width, height), Image.Resampling.LANCZOS)
            directory = output / variant
            directory.mkdir(parents=True, exist_ok=True)
            path = directory / f"{shot}.webp"
            resized.save(path, "WEBP", quality=WEBP_QUALITY, method=6)
            print(f"{variant}/{path.name}: {width} x {height}, {path.stat().st_size:,} bytes")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("captures", type=Path, help="Directory with the two verified .jpg browser captures")
    parser.add_argument("--output", type=Path, default=ASSETS)
    args = parser.parse_args()
    prepare(args.captures, args.output)


if __name__ == "__main__":
    main()
