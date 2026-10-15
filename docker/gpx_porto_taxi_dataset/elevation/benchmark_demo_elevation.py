"""Compare creation of the same 1,000 public demo tracks, including cold tile expansion."""
import argparse
import json
import math
import platform
import resource
import statistics
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

from .create_demo_tracks import generate_tracks

BENCHMARK_TRACK_COUNT = 1000
BENCHMARK_REPETITIONS = 5


def benchmark(archive, raster, output, alternate_raster=None):
    output.mkdir(parents=True, exist_ok=True)
    results = {"without_elevation": [], "with_elevation": []}
    rasters = {"without_elevation": None, "with_elevation": raster}
    if alternate_raster is not None:
        results["with_numeric_elevation"] = []
        rasters["with_numeric_elevation"] = alternate_raster
    for repeat in range(BENCHMARK_REPETITIONS):
        modes = list(results)
        rotation = repeat % len(modes)
        modes = modes[rotation:] + modes[:rotation]
        for mode in modes:
            directory = output / f"{mode}-{repeat}"
            if directory.exists():
                raise ValueError(f"Benchmark output already exists: {directory}")
            result = generate_tracks(archive, directory, rasters[mode],
                                     limit=BENCHMARK_TRACK_COUNT, catalog_path=None)
            results[mode].append(result)
    # Verify every generated point after timing; timestamps/coordinates must remain identical.
    points = 0
    minimum, maximum = float("inf"), float("-inf")
    with zipfile.ZipFile(archive) as source:
        for path in sorted((output / "with_elevation-0").glob("*.gpx")):
            if alternate_raster is not None and path.read_bytes() != (
                    output / "with_numeric_elevation-0" / path.name).read_bytes():
                raise ValueError(f"Numeric and WebP interpolation results differ: {path.name}")
            original = list(ET.fromstring(source.read(path.name)).iter("trkpt"))
            generated = list(ET.parse(path).getroot().iter("trkpt"))
            if len(original) != len(generated):
                raise ValueError(f"Track point count changed: {path.name}")
            for a, b in zip(original, generated):
                if a.attrib != b.attrib or a.find("time").text != b.find("time").text:
                    raise ValueError(f"Track coordinates or timestamps changed: {path.name}")
                height = float(b.find("ele").text)
                if not math.isfinite(height):
                    raise ValueError(f"Non-finite elevation: {path.name}")
                points += 1
                minimum, maximum = min(minimum, height), max(maximum, height)
    baseline = statistics.median(r["duration_seconds"] for r in results["without_elevation"])
    enriched = statistics.median(r["duration_seconds"] for r in results["with_elevation"])
    report = {"tracks": BENCHMARK_TRACK_COUNT, "points": points, "repetitions": BENCHMARK_REPETITIONS,
              "python": platform.python_version(), "platform": platform.platform(),
              "compressed_raster_bytes": raster.stat().st_size, "baseline_median_seconds": baseline,
              "elevation_median_seconds": enriched, "overhead_seconds": enriched - baseline,
              "overhead_ms_per_track": (enriched - baseline) * 1000 / BENCHMARK_TRACK_COUNT,
              "min_elevation_m": minimum, "max_elevation_m": maximum,
              "max_rss_kib": resource.getrusage(resource.RUSAGE_SELF).ru_maxrss, "runs": results}
    report["summaries"] = {mode: {
        "median_seconds": statistics.median(run["duration_seconds"] for run in runs),
        "median_tile_expansion_seconds": statistics.median(run["tile_expansion_seconds"] for run in runs),
        "compressed_raster_bytes": rasters[mode].stat().st_size if rasters[mode] else 0,
    } for mode, runs in results.items()}
    (output / "benchmark.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps({key: value for key, value in report.items() if key != "runs"}, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("raster", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--alternate-raster", type=Path)
    args = parser.parse_args()
    benchmark(args.archive, args.raster, args.output, args.alternate_raster)
