import json
import sqlite3
import sys
import tempfile
import threading
import unittest
import urllib.error
import urllib.parse
import urllib.request
from http.server import ThreadingHTTPServer
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from location_search_server import SearchConfig, SearchEngine, LocationSearchHandler


SCHEMA_SQL = """
CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL);
CREATE TABLE places (
  id INTEGER PRIMARY KEY,
  geoname_id INTEGER NOT NULL UNIQUE,
  display_name TEXT NOT NULL,
  normalized_name TEXT NOT NULL,
  alternate_names TEXT NOT NULL,
  source_kind TEXT NOT NULL,
  feature_class TEXT NOT NULL,
  feature_code TEXT NOT NULL,
  kind TEXT NOT NULL,
  lat REAL NOT NULL,
  lon REAL NOT NULL,
  country_code TEXT,
  country_name TEXT,
  admin1_code TEXT,
  admin1_name TEXT,
  population INTEGER,
  elevation INTEGER,
  importance_score REAL NOT NULL
);
CREATE VIRTUAL TABLE place_names_fts USING fts5(
  normalized_name,
  alternate_names,
  content='places',
  content_rowid='id',
  tokenize='unicode61 remove_diacritics 2'
);
CREATE INDEX idx_places_country_admin ON places(country_code, admin1_code);
CREATE INDEX idx_places_lat_lon ON places(lat, lon);
CREATE INDEX idx_places_kind ON places(kind);
"""


class LocationSearchServerTest(unittest.TestCase):
    def test_status_reports_missing_db_cleanly(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            engine = SearchEngine(SearchConfig(db_path=Path(temp_dir) / "missing.sqlite"))

            status = engine.status()

            self.assertFalse(status["ready"])
            self.assertEqual(status["phase"], "unavailable")
            self.assertIn("missing", status["message"])
            self.assertIn("versionInfo", status)
            self.assertEqual(status["versionInfo"]["components"]["sqlite"], sqlite3.sqlite_version)

    def test_search_exact_ascii_prefix_peak_distance_and_short_query(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            db_path = Path(temp_dir) / "geonames-search.sqlite"
            create_sample_db(db_path)
            engine = SearchEngine(SearchConfig(db_path=db_path))

            status = engine.status()
            self.assertTrue(status["ready"])
            self.assertEqual(status["row_count"], 8)
            self.assertEqual(status["source_attribution"], "GeoNames")

            exact = engine.search("Zürich", 20, "importance", None, None)
            self.assertEqual(first_names(exact)[0], "Zürich")

            ascii_fallback = engine.search("Zurich", 20, "importance", None, None)
            self.assertEqual(first_names(ascii_fallback)[0], "Zürich")

            prefix = engine.search("Glatt", 20, "importance", None, None)
            self.assertIn("Glattbrugg", first_names(prefix))

            for peak in ("Säntis", "Matterhorn", "Eiger"):
                result = engine.search(peak, 20, "importance", None, None)
                self.assertEqual(first_names(result)[0], peak)
                self.assertEqual(result["results"][0]["kind"], "peak")

            nearby = engine.search("Zurich", 20, "distance", 47.38, 8.54)
            self.assertEqual(nearby["sort"], "distance")
            self.assertEqual(first_names(nearby)[0], "Zürich")
            self.assertLess(nearby["results"][0]["distance_meters"], 1_000)

            short = engine.search("a", 20, "importance", None, None)
            self.assertFalse(short["ready"])
            self.assertEqual(short["phase"], "invalid-request")

    def test_http_status_and_search_require_backend_identity_params(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            db_path = Path(temp_dir) / "geonames-search.sqlite"
            create_sample_db(db_path)
            LocationSearchHandler.engine = SearchEngine(SearchConfig(db_path=db_path))
            server = ThreadingHTTPServer(("127.0.0.1", 0), LocationSearchHandler)
            thread = threading.Thread(target=server.serve_forever, daemon=True)
            thread.start()

            base_url = f"http://127.0.0.1:{server.server_address[1]}"
            try:
                self.assertEqual(read_text(f"{base_url}/health"), "ok")

                with self.assertRaises(urllib.error.HTTPError) as status_error:
                    read_json(f"{base_url}/status")
                self.assertEqual(status_error.exception.code, 403)
                self.assertEqual(json.loads(status_error.exception.read())["phase"], "forbidden")
                status_error.exception.close()

                with self.assertRaises(urllib.error.HTTPError) as search_error:
                    read_json(f"{base_url}/search?q=Zurich")
                self.assertEqual(search_error.exception.code, 403)
                search_error.exception.close()

                auth = urllib.parse.urlencode({"mtl-version": "test-1", "mtl-server-id": "server-a"})
                status = read_json(f"{base_url}/status?{auth}")
                self.assertTrue(status["ready"])
                self.assertIn("versionInfo", status)

                search_params = urllib.parse.urlencode(
                    {
                        "mtl-version": "test-1",
                        "mtl-server-id": "server-a",
                        "q": "Zurich",
                    }
                )
                search = read_json(f"{base_url}/search?{search_params}")
                self.assertTrue(search["ready"])
                self.assertEqual(first_names(search)[0], "Zürich")
            finally:
                server.shutdown()
                server.server_close()
                thread.join(timeout=5)


def create_sample_db(db_path: Path) -> None:
    connection = sqlite3.connect(db_path)
    try:
        connection.executescript(SCHEMA_SQL)
        places = [
            (1, 2657896, "Zürich", "zurich", "zuerich", "geonames", "P", "PPLA", "city", 47.3769, 8.5417, "CH", "Switzerland", "ZH", "Zürich", 341730, None, 136.0),
            (2, 5177568, "Zurich", "zurich", "", "geonames", "P", "PPL", "village", 41.2223, -82.4885, "US", "United States", "OH", "Ohio", 935, None, 95.0),
            (3, 2660512, "Glattbrugg", "glattbrugg", "", "geonames", "P", "PPL", "town", 47.4313, 8.5627, "CH", "Switzerland", "ZH", "Zürich", 12000, None, 112.0),
            (4, 2658822, "Säntis", "santis", "saentis", "geonames", "T", "PK", "peak", 47.2494, 9.3433, "CH", "Switzerland", "SG", "Sankt Gallen", None, 2502, 88.0),
            (5, 2659696, "Matterhorn", "matterhorn", "", "geonames", "T", "PK", "peak", 45.9763, 7.6586, "CH", "Switzerland", "VS", "Valais", None, 4478, 90.0),
            (6, 2660880, "Eiger", "eiger", "", "geonames", "T", "PK", "peak", 46.5776, 8.0053, "CH", "Switzerland", "BE", "Bern", None, 3970, 89.0),
            (7, 1, "Zurichberg", "zurichberg", "zuerichberg", "geonames", "T", "HLL", "hill", 47.384, 8.57, "CH", "Switzerland", "ZH", "Zürich", None, 679, 70.0),
            (8, 2, "Zürich Pass", "zurich pass", "", "geonames", "T", "PASS", "pass", 47.4, 8.6, "CH", "Switzerland", "ZH", "Zürich", None, 800, 68.0),
        ]
        connection.executemany(
            """
            INSERT INTO places (
              id, geoname_id, display_name, normalized_name, alternate_names,
              source_kind, feature_class, feature_code, kind, lat, lon,
              country_code, country_name, admin1_code, admin1_name,
              population, elevation, importance_score
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            places,
        )
        connection.executemany(
            "INSERT INTO place_names_fts(rowid, normalized_name, alternate_names) VALUES (?, ?, ?)",
            [(row[0], row[3], row[4]) for row in places],
        )
        connection.executemany(
            "INSERT INTO metadata(key, value) VALUES (?, ?)",
            [
                ("schema_version", "geonames-search-v1"),
                ("build_time", "2026-05-24T00:00:00Z"),
                ("source_attribution", "GeoNames"),
                ("source_license", "CC-BY 4.0"),
                ("source_license_url", "https://creativecommons.org/licenses/by/4.0/"),
                ("row_count", "8"),
                ("populated_place_count", "3"),
                ("terrain_count", "5"),
            ],
        )
        connection.commit()
    finally:
        connection.close()


def first_names(response):
    return [result["display_name"] for result in response["results"]]


def read_text(url: str) -> str:
    with urllib.request.urlopen(url, timeout=3) as response:
        return response.read().decode("utf-8")


def read_json(url: str):
    return json.loads(read_text(url))


if __name__ == "__main__":
    unittest.main()
