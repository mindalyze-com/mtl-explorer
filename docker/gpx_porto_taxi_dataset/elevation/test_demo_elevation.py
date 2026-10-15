"""Check terrain interpolation, track preservation, and interrupted creation."""
import gzip
import json
import lzma
import math
import random
import tempfile
import unittest
import xml.etree.ElementTree as ET
import zipfile
from array import array
from collections import Counter
from pathlib import Path
from unittest.mock import patch

from .create_demo_tracks import GENERATION_MARKER, generate_tracks, inject_elevations, inject_track_descriptions
from .demo_elevation import ElevationRaster, HEADER_LENGTH, RASTER_MAGIC, RASTER_VERSION
from demo_track_catalog import GENERATED_CATALOG_NAME, build_catalog, load_tracks, sha256
from .numeric_elevation import encode_numeric, decode_numeric


def coordinate(x, y, scale=4):
    return (math.degrees(math.atan(math.sinh(math.pi * (1 - 2 * y / scale)))),
            x / scale * 360 - 180)


def write_raster(path):
    tiles, payloads, chunks = {}, [], []
    offset = 0
    for tx in range(2):
        for ty in range(2):
            values = array("H", [32768 + (ty * 2 + y) * 100 + (tx * 2 + x) * 10 - 50
                                 for y in range(2) for x in range(2)])
            compressed = lzma.compress(values.tobytes())
            tiles[f"{tx}/{ty}"] = len(chunks)
            chunks.append(compressed)
            payloads.append([offset, len(compressed), "xz"])
            offset += len(compressed)
    metadata = {"version": RASTER_VERSION, "tile_size": 2, "zoom": 1,
                "bounds": [-180, -85, 180, 85], "tiles": tiles, "payloads": payloads}
    header = json.dumps(metadata).encode()
    path.write_bytes(RASTER_MAGIC + HEADER_LENGTH.pack(len(header)) + header + b"".join(chunks))


def track_xml(prefix="", trip_id="synthetic-1"):
    namespace = ' xmlns:g="http://www.topografix.com/GPX/1/1"' if prefix else ""
    points = []
    for index, (x, y) in enumerate(((1.5, 1.5), (2.0, 2.0), (2.5, 2.5))):
        latitude, longitude = coordinate(x, y)
        points.append(f'<{prefix}trkpt lon="{longitude}" lat="{latitude}">'
                      f'<{prefix}time>2026-01-01T10:0{index}:00Z</{prefix}time>'
                      f'</{prefix}trkpt>')
    return (f'<{prefix}gpx{namespace}><{prefix}trk>'
            f'<{prefix}name>Demo Trip {trip_id}</{prefix}name>'
            f'<{prefix}cmt>Original Trip ID: {trip_id}</{prefix}cmt>'
            f'<{prefix}trkseg>' + "".join(points)
            + f'</{prefix}trkseg></{prefix}trk></{prefix}gpx>').encode()


class DemoElevationTest(unittest.TestCase):
    def test_packaged_raster_matches_checksum_config_and_all_tiles_expand(self):
        directory = Path(__file__).parent
        path = directory / "data" / "porto-elevation-120m.mtldem"
        summary = json.loads(path.with_suffix(".json").read_text())
        config = json.loads((directory / "demo-elevation-config.json").read_text())
        self.assertEqual(summary["raster_bytes"], path.stat().st_size)
        self.assertEqual(summary["sha256"], sha256(path))
        self.assertTrue(json.loads(path.with_name("elevation-attribution.json").read_text()))
        with ElevationRaster(path) as raster:
            for key, value in config.items():
                self.assertEqual(value, raster.metadata[key], key)
            self.assertEqual(summary["unique_tiles"], len(raster.payloads))
            self.assertEqual({key: value for key, value in summary["codecs"].items() if value},
                             dict(Counter(payload[2] for payload in raster.payloads)))
            for index in range(len(raster.payloads)):
                raster._tile(index)
            self.assertLessEqual(len(raster.cache), raster.cache_size)

    def test_numeric_predictors_round_trip_signed_heights_and_reject_truncation(self):
        rng = random.Random(2)
        values = array("H", [32768 - 20, 32768, 32768 + 100, 65535, 0] +
                       [rng.randrange(65536) for _ in range(59)])
        for predictor in ("left", "gradient", "median"):
            encoded = encode_numeric(values, 8, predictor)
            self.assertEqual(values, decode_numeric(encoded, 8, predictor))
            with self.assertRaisesRegex(ValueError, "Truncated"):
                decode_numeric(encoded[:-1], 8, predictor)

    def test_plane_negative_heights_pixel_centres_tile_seams_and_bounded_cache(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "raster.mtldem"
            write_raster(path)
            with ElevationRaster(path, cache_size=2) as raster:
                for x, y, expected in ((0.5, 0.5, -50), (1.5, 1.5, 60), (2, 2, 115),
                                       (2.5, 2.5, 170), (2.00001, 2, 115.0001)):
                    self.assertAlmostEqual(expected, raster.elevation(*coordinate(x, y)), places=5)
                self.assertLessEqual(len(raster.cache), 2)
                with self.assertRaisesRegex(ValueError, "outside demo elevation coverage"):
                    raster.elevation(89, 0)
                with self.assertRaisesRegex(ValueError, "Non-finite"):
                    raster.elevation(float("nan"), 0)

    def test_random_weights_retain_cell_range_and_exact_samples(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "raster.mtldem"
            write_raster(path)
            with ElevationRaster(path) as raster:
                rng = random.Random(1)
                samples = [raster.elevation(*coordinate(2, 2), rng) for _ in range(100)]
                self.assertTrue(all(60 <= height <= 170 for height in samples))
                self.assertGreater(max(samples) - min(samples), 1)
                self.assertEqual(60, raster.elevation(*coordinate(1.5, 1.5), rng))

    def test_xml_coordinates_times_recorded_elevation_and_namespaces_are_preserved(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "raster.mtldem"
            write_raster(path)
            with ElevationRaster(path) as raster:
                for prefix in ("", "g:"):
                    original = track_xml(prefix)
                    enriched, added = inject_elevations(original, raster, "track.gpx")
                    repeated, _ = inject_elevations(original, raster, "track.gpx")
                    self.assertEqual(3, added)
                    self.assertEqual(enriched, repeated)
                    namespace = "{http://www.topografix.com/GPX/1/1}" if prefix else ""
                    before = list(ET.fromstring(original).iter(namespace + "trkpt"))
                    after = list(ET.fromstring(enriched).iter(namespace + "trkpt"))
                    for a, b in zip(before, after):
                        self.assertEqual(a.attrib, b.attrib)
                        self.assertEqual(a.find(namespace + "time").text, b.find(namespace + "time").text)
                        self.assertTrue(math.isfinite(float(b.find(namespace + "ele").text)))
                    recorded, added = inject_elevations(enriched, raster, "track.gpx")
                    self.assertEqual(0, added)
                    self.assertEqual(enriched, recorded)

    def test_fresh_tracks_have_unique_importable_descriptions_and_keep_catalog_valid(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            raster = root / "raster.mtldem"
            write_raster(raster)
            archive = root / "tracks.zip"
            identifiers = ("synthetic-1", "T-synthetic-2")
            with zipfile.ZipFile(archive, "w") as output:
                for index, trip_id in enumerate(identifiers):
                    output.writestr(f"track-{index}.gpx", track_xml(trip_id=trip_id))
            catalog = root / "catalog.gz"
            build_catalog(archive, catalog)
            directory = root / "tracks"
            result = generate_tracks(archive, directory, raster, catalog_path=catalog)
            self.assertEqual(2, result["created"])
            descriptions = []
            for index, trip_id in enumerate(identifiers):
                track = ET.parse(directory / f"track-{index}.gpx").getroot().find("trk")
                description = track.find("desc").text
                self.assertEqual(1, len(track.findall("desc")))
                self.assertIn(f"(trip ID: {trip_id}).", description)
                self.assertIn("Recording dates were changed", description)
                self.assertIn("the original data contains no elevation measurements", description)
                self.assertIn("randomly assigned photos on some tracks and synthetic heart rate data", description)
                self.assertEqual(f"Demo Trip {trip_id}", track.find("name").text)
                self.assertEqual(["name", "cmt", "desc", "trkseg"], [child.tag for child in track])
                descriptions.append(description)
            self.assertEqual(2, len(set(descriptions)))
            with patch("generate_demo_photos.collect_tracks", side_effect=AssertionError("fallback")):
                self.assertEqual(2, len(load_tracks(directory, catalog, archive)))

    def test_description_preserves_track_bytes_namespaces_and_existing_metadata(self):
        for prefix in ("", "g:"):
            with self.subTest(prefix=prefix):
                namespace = "{http://www.topografix.com/GPX/1/1}" if prefix else ""
                original = track_xml(prefix, "synthetic&amp;1")
                original = original.replace(
                    f'<{prefix}trk>'.encode(),
                    (f'<{prefix}metadata><{prefix}desc>Source note</{prefix}desc></{prefix}metadata>'
                     f'<{prefix}trk>').encode())
                original = original.replace(
                    f'<{prefix}trkseg>'.encode(),
                    f'<{prefix}desc>Old description</{prefix}desc><{prefix}trkseg>'.encode())
                enriched = inject_track_descriptions(original, "synthetic.gpx")
                self.assertEqual(enriched, inject_track_descriptions(enriched, "synthetic.gpx"))
                before = ET.fromstring(original)
                after = ET.fromstring(enriched)
                self.assertEqual("Source note", after.find(f"{namespace}metadata/{namespace}desc").text)
                track = after.find(namespace + "trk")
                self.assertEqual(1, len(track.findall(namespace + "desc")))
                self.assertIn("trip ID: synthetic&1", track.find(namespace + "desc").text)
                self.assertEqual(before.find(f"{namespace}trk/{namespace}name").text,
                                 track.find(namespace + "name").text)
                start = f'<{prefix}trkseg>'.encode()
                self.assertEqual(original[original.index(start):], enriched[enriched.index(start):])

    def test_description_rejects_missing_source_id_instead_of_reusing_generic_text(self):
        for original in (b"<gpx/>", b"<gpx><trk><name>Unnamed</name><trkseg/></trk></gpx>"):
            with self.subTest(original=original):
                with self.assertRaisesRegex(ValueError, "missing (original Porto trip ID|track)"):
                    inject_track_descriptions(original, "synthetic.gpx")

    def test_interruption_resumes_and_generated_catalog_avoids_fallback(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            raster = root / "raster.mtldem"
            write_raster(raster)
            archive = root / "tracks.zip"
            with zipfile.ZipFile(archive, "w") as output:
                for index in range(3):
                    output.writestr(f"track-{index}.gpx", track_xml())
            catalog = root / "catalog.gz"
            build_catalog(archive, catalog)
            real = inject_elevations
            calls = 0
            def interrupted(*args):
                nonlocal calls
                calls += 1
                if calls == 2:
                    raise OSError("synthetic interruption")
                return real(*args)
            directory = root / "tracks"
            with patch("elevation.create_demo_tracks.inject_elevations", side_effect=interrupted):
                with self.assertRaisesRegex(OSError, "synthetic interruption"):
                    generate_tracks(archive, directory, raster, catalog_path=catalog)
            self.assertTrue((directory / GENERATION_MARKER).exists())
            first = directory / "track-0.gpx"
            previous = first.read_bytes(), first.stat().st_mtime_ns
            result = generate_tracks(archive, directory, raster, catalog_path=catalog)
            self.assertEqual(2, result["created"])
            self.assertEqual(1, result["resumed"])
            self.assertEqual(previous, (first.read_bytes(), first.stat().st_mtime_ns))
            self.assertFalse((directory / GENERATION_MARKER).exists())
            self.assertEqual(3, len(json.loads(gzip.decompress(
                (directory / GENERATED_CATALOG_NAME).read_bytes()))["extracted_files"]))
            with patch("generate_demo_photos.collect_tracks", side_effect=AssertionError("fallback")):
                self.assertEqual(3, len(load_tracks(directory, catalog, archive)))


if __name__ == "__main__":
    unittest.main()
