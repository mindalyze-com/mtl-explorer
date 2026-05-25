import sqlite3
import tempfile
import unittest
import zipfile
from pathlib import Path

import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from build_geonames_search import build_database, normalize_name


def geonames_row(
    geoname_id: int,
    name: str,
    ascii_name: str,
    alternate_names: str,
    feature_class: str,
    feature_code: str,
    population: int,
    elevation: str = "",
    country_code: str = "CH",
    admin1_code: str = "ZH",
) -> str:
    columns = [
        str(geoname_id),
        name,
        ascii_name,
        alternate_names,
        "47.0000",
        "8.0000",
        feature_class,
        feature_code,
        country_code,
        "",
        admin1_code,
        "",
        "",
        "",
        str(population),
        elevation,
        "500",
        "Europe/Zurich",
        "2026-05-24",
    ]
    return "\t".join(columns)


class GeoNamesSearchBuilderTest(unittest.TestCase):
    def test_normalizes_accents_like_location_search(self):
        self.assertEqual(normalize_name("Zürich HB / Säntis"), "zurich hb santis")

    def test_builds_filtered_sqlite_fts_database(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            all_countries_zip = temp / "allCountries.zip"
            admin1_codes = temp / "admin1CodesASCII.txt"
            country_info = temp / "countryInfo.txt"
            output = temp / "geonames-search.sqlite"

            rows = [
                geonames_row(2657896, "Zürich", "Zurich", "Zurich,Zuerich", "P", "PPLA", 341730),
                geonames_row(1, "Tiny", "Tiny", "", "P", "PPL", 500),
                geonames_row(2, "Seat", "Seat", "", "P", "PPLA4", 0),
                geonames_row(3, "Säntis", "Santis", "Saentis", "T", "PK", 0, "2502"),
                geonames_row(4, "Unsupported", "Unsupported", "", "T", "VAL", 0),
                geonames_row(5, "River", "River", "", "H", "STM", 0),
            ]
            with zipfile.ZipFile(all_countries_zip, "w") as archive:
                archive.writestr("allCountries.txt", "\n".join(rows) + "\n")
            admin1_codes.write_text("CH.ZH\tZürich\tZurich\t2657895\n", encoding="utf-8")
            country_info.write_text("# header\nCH\tCHE\t756\tSZ\tSwitzerland\n", encoding="utf-8")

            counts = build_database(all_countries_zip, admin1_codes, output, country_info)

            self.assertEqual(counts["row_count"], 3)
            self.assertEqual(counts["populated_place_count"], 2)
            self.assertEqual(counts["terrain_count"], 1)

            with sqlite3.connect(output) as connection:
                places = connection.execute(
                    "SELECT display_name, normalized_name, kind, admin1_name, country_name "
                    "FROM places ORDER BY geoname_id"
                ).fetchall()
                self.assertEqual(
                    places,
                    [
                        ("Seat", "seat", "city", "Zürich", "Switzerland"),
                        ("Säntis", "santis", "peak", "Zürich", "Switzerland"),
                        ("Zürich", "zurich", "city", "Zürich", "Switzerland"),
                    ],
                )
                fts = connection.execute(
                    "SELECT p.display_name FROM place_names_fts f "
                    "JOIN places p ON p.id = f.rowid "
                    "WHERE place_names_fts MATCH 'zurich'"
                ).fetchall()
                self.assertEqual(fts, [("Zürich",)])
                metadata = dict(connection.execute("SELECT key, value FROM metadata").fetchall())
                self.assertEqual(metadata["source_attribution"], "GeoNames")
                self.assertEqual(metadata["source_license"], "CC-BY 4.0")


if __name__ == "__main__":
    unittest.main()
