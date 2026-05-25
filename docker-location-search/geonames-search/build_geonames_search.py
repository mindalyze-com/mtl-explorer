#!/usr/bin/env python3
"""Build the MTL Explorer GeoNames SQLite FTS gazetteer."""

from __future__ import annotations

import argparse
import csv
import json
import math
import os
import re
import sqlite3
import tempfile
import unicodedata
import zipfile
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable

SCHEMA_VERSION = "geonames-search-v1"
SOURCE_ALL_COUNTRIES_URL = "https://download.geonames.org/export/dump/allCountries.zip"
SOURCE_ADMIN1_URL = "https://download.geonames.org/export/dump/admin1CodesASCII.txt"
SOURCE_COUNTRY_INFO_URL = "https://download.geonames.org/export/dump/countryInfo.txt"
SOURCE_ATTRIBUTION = "GeoNames"
SOURCE_LICENSE = "CC-BY 4.0"
SOURCE_LICENSE_URL = "https://creativecommons.org/licenses/by/4.0/"

GEONAME_ID = 0
NAME = 1
ASCII_NAME = 2
ALTERNATE_NAMES = 3
LATITUDE = 4
LONGITUDE = 5
FEATURE_CLASS = 6
FEATURE_CODE = 7
COUNTRY_CODE = 8
ADMIN1_CODE = 10
POPULATION = 14
ELEVATION = 15
MODIFICATION_DATE = 18
GEONAME_COLUMN_COUNT = 19

POPULATED_PLACE_CLASS = "P"
TERRAIN_CLASS = "T"
POPULATED_PLACE_MIN_POPULATION = 500
ADMIN_SEAT_CODES = frozenset({"PPLC", "PPLA", "PPLA2", "PPLA3", "PPLA4"})
SUPPORTED_TERRAIN_CODES = frozenset({"PK", "MT", "MTS", "HLL", "PASS", "RDGE"})
TERRAIN_KINDS = {
    "PK": "peak",
    "MT": "mountain",
    "MTS": "mountain",
    "HLL": "hill",
    "PASS": "pass",
    "RDGE": "ridge",
}
FEATURE_PRIORITY = {
    "capital": 100.0,
    "city": 92.0,
    "town": 82.0,
    "village": 72.0,
    "neighbourhood": 50.0,
    "peak": 76.0,
    "mountain": 68.0,
    "pass": 64.0,
    "ridge": 60.0,
    "hill": 56.0,
}

METADATA_TABLE_SQL = """
CREATE TABLE metadata (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL
)
"""

PLACES_TABLE_SQL = """
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
)
"""

FTS_TABLE_SQL = """
CREATE VIRTUAL TABLE place_names_fts USING fts5(
  normalized_name,
  alternate_names,
  content='places',
  content_rowid='id',
  tokenize='unicode61 remove_diacritics 2'
)
"""

INDEX_SQL = (
    "CREATE INDEX idx_places_country_admin ON places(country_code, admin1_code)",
    "CREATE INDEX idx_places_lat_lon ON places(lat, lon)",
    "CREATE INDEX idx_places_kind ON places(kind)",
)


@dataclass(frozen=True)
class PlaceRecord:
    id: int
    geoname_id: int
    display_name: str
    normalized_name: str
    alternate_names: str
    source_kind: str
    feature_class: str
    feature_code: str
    kind: str
    lat: float
    lon: float
    country_code: str | None
    country_name: str | None
    admin1_code: str | None
    admin1_name: str | None
    population: int | None
    elevation: int | None
    importance_score: float


def normalize_name(value: str) -> str:
    decomposed = unicodedata.normalize("NFKD", value)
    without_marks = "".join(ch for ch in decomposed if not unicodedata.combining(ch))
    lowered = without_marks.lower()
    cleaned = "".join(ch if ch.isalnum() else " " for ch in lowered)
    return re.sub(r"\s+", " ", cleaned).strip()


def parse_int(value: str) -> int | None:
    value = value.strip()
    if not value:
        return None
    return int(value)


def parse_float(value: str) -> float:
    return float(value.strip())


def load_admin1_names(path: Path) -> dict[str, str]:
    names: dict[str, str] = {}
    with path.open("r", encoding="utf-8", newline="") as source:
        reader = csv.reader(source, delimiter="\t")
        for row in reader:
            if len(row) >= 2 and row[0] and row[1]:
                names[row[0]] = row[1]
    return names


def load_country_names(path: Path | None) -> dict[str, str]:
    if path is None:
        return {}
    names: dict[str, str] = {}
    with path.open("r", encoding="utf-8", newline="") as source:
        reader = csv.reader(source, delimiter="\t")
        for row in reader:
            if not row or row[0].startswith("#") or len(row) < 5:
                continue
            names[row[0]] = row[4]
    return names


def iter_geonames_rows(all_countries_zip: Path) -> Iterable[list[str]]:
    with zipfile.ZipFile(all_countries_zip) as archive:
        member = next(
            (name for name in archive.namelist() if Path(name).name == "allCountries.txt"),
            None,
        )
        if member is None:
            raise ValueError(f"{all_countries_zip} does not contain allCountries.txt")
        with archive.open(member) as raw:
            text = (line.decode("utf-8").rstrip("\n") for line in raw)
            reader = csv.reader(text, delimiter="\t")
            for row in reader:
                if len(row) >= GEONAME_COLUMN_COUNT:
                    yield row


def source_timestamp_for_zip(zip_path: Path, member_name: str) -> str:
    with zipfile.ZipFile(zip_path) as archive:
        info = next(
            (info for info in archive.infolist() if Path(info.filename).name == member_name),
            None,
        )
        if info is None:
            return file_mtime_timestamp(zip_path)
        return datetime(*info.date_time, tzinfo=timezone.utc).isoformat().replace("+00:00", "Z")


def file_mtime_timestamp(path: Path | None) -> str:
    if path is None:
        return ""
    return datetime.fromtimestamp(path.stat().st_mtime, timezone.utc).isoformat().replace("+00:00", "Z")


def is_supported_row(row: list[str]) -> bool:
    feature_class = row[FEATURE_CLASS]
    feature_code = row[FEATURE_CODE]
    if feature_class == POPULATED_PLACE_CLASS:
        population = parse_int(row[POPULATION]) or 0
        return population > POPULATED_PLACE_MIN_POPULATION or feature_code in ADMIN_SEAT_CODES
    return feature_class == TERRAIN_CLASS and feature_code in SUPPORTED_TERRAIN_CODES


def kind_for(row: list[str], population: int | None) -> str:
    feature_class = row[FEATURE_CLASS]
    feature_code = row[FEATURE_CODE]
    if feature_class == TERRAIN_CLASS:
        return TERRAIN_KINDS[feature_code]
    if feature_code == "PPLC":
        return "capital"
    if feature_code in {"PPLA", "PPLA2", "PPLA3", "PPLA4"}:
        return "city"
    if feature_code in {"PPLX", "PPLQ"}:
        return "neighbourhood"
    safe_population = population or 0
    if safe_population >= 100_000:
        return "city"
    if safe_population >= 5_000:
        return "town"
    return "village"


def compact_alternate_names(row: list[str], normalized_display_name: str) -> str:
    names = [row[NAME], row[ASCII_NAME]]
    if row[ALTERNATE_NAMES].strip():
        names.extend(row[ALTERNATE_NAMES].split(","))
    normalized: list[str] = []
    seen: set[str] = set()
    for name in names:
        value = normalize_name(name)
        if value and value not in seen and value != normalized_display_name:
            seen.add(value)
            normalized.append(value)
    return " ".join(normalized)


def importance_score(kind: str, population: int | None, elevation: int | None) -> float:
    score = FEATURE_PRIORITY[kind]
    if population:
        score += min(math.log10(population + 1) * 8.0, 64.0)
    if elevation and kind in {"peak", "mountain", "hill"}:
        score += min(math.log10(max(elevation, 0) + 1) * 3.0, 16.0)
    return round(score, 6)


def build_record(
    row_id: int,
    row: list[str],
    admin1_names: dict[str, str],
    country_names: dict[str, str],
) -> PlaceRecord:
    display_name = row[NAME].strip() or row[ASCII_NAME].strip()
    if not display_name:
        raise ValueError(f"GeoNames row {row[GEONAME_ID]} has no display name")
    normalized_name = normalize_name(display_name)
    population = parse_int(row[POPULATION])
    elevation = parse_int(row[ELEVATION])
    kind = kind_for(row, population)
    country_code = row[COUNTRY_CODE].strip() or None
    admin1_code = row[ADMIN1_CODE].strip() or None
    admin1_key = f"{country_code}.{admin1_code}" if country_code and admin1_code else None
    return PlaceRecord(
        id=row_id,
        geoname_id=int(row[GEONAME_ID]),
        display_name=display_name,
        normalized_name=normalized_name,
        alternate_names=compact_alternate_names(row, normalized_name),
        source_kind="geonames",
        feature_class=row[FEATURE_CLASS],
        feature_code=row[FEATURE_CODE],
        kind=kind,
        lat=parse_float(row[LATITUDE]),
        lon=parse_float(row[LONGITUDE]),
        country_code=country_code,
        country_name=country_names.get(country_code or ""),
        admin1_code=admin1_code,
        admin1_name=admin1_names.get(admin1_key or ""),
        population=population,
        elevation=elevation,
        importance_score=importance_score(kind, population, elevation),
    )


def create_schema(connection: sqlite3.Connection) -> None:
    connection.executescript(
        """
        PRAGMA journal_mode = OFF;
        PRAGMA synchronous = OFF;
        PRAGMA temp_store = MEMORY;
        """
    )
    connection.execute(METADATA_TABLE_SQL)
    connection.execute(PLACES_TABLE_SQL)
    connection.execute(FTS_TABLE_SQL)
    for statement in INDEX_SQL:
        connection.execute(statement)


def insert_places(connection: sqlite3.Connection, records: Iterable[PlaceRecord]) -> dict[str, int]:
    counts = {"row_count": 0, "populated_place_count": 0, "terrain_count": 0}
    place_sql = """
        INSERT INTO places (
          id, geoname_id, display_name, normalized_name, alternate_names,
          source_kind, feature_class, feature_code, kind, lat, lon,
          country_code, country_name, admin1_code, admin1_name,
          population, elevation, importance_score
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """
    fts_sql = """
        INSERT INTO place_names_fts(rowid, normalized_name, alternate_names)
        VALUES (?, ?, ?)
    """
    with connection:
        for record in records:
            connection.execute(place_sql, (
                record.id,
                record.geoname_id,
                record.display_name,
                record.normalized_name,
                record.alternate_names,
                record.source_kind,
                record.feature_class,
                record.feature_code,
                record.kind,
                record.lat,
                record.lon,
                record.country_code,
                record.country_name,
                record.admin1_code,
                record.admin1_name,
                record.population,
                record.elevation,
                record.importance_score,
            ))
            connection.execute(fts_sql, (record.id, record.normalized_name, record.alternate_names))
            counts["row_count"] += 1
            if record.feature_class == POPULATED_PLACE_CLASS:
                counts["populated_place_count"] += 1
            elif record.feature_class == TERRAIN_CLASS:
                counts["terrain_count"] += 1
    return counts


def write_metadata(
    connection: sqlite3.Connection,
    counts: dict[str, int],
    all_countries_zip: Path,
    admin1_codes: Path,
    country_info: Path | None,
) -> None:
    metadata = {
        "schema_version": SCHEMA_VERSION,
        "build_time": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "source_all_countries_url": SOURCE_ALL_COUNTRIES_URL,
        "source_admin1_url": SOURCE_ADMIN1_URL,
        "source_country_info_url": SOURCE_COUNTRY_INFO_URL if country_info else "",
        "source_all_countries_timestamp": source_timestamp_for_zip(all_countries_zip, "allCountries.txt"),
        "source_admin1_timestamp": file_mtime_timestamp(admin1_codes),
        "source_country_info_timestamp": file_mtime_timestamp(country_info) if country_info else "",
        "source_attribution": SOURCE_ATTRIBUTION,
        "source_license": SOURCE_LICENSE,
        "source_license_url": SOURCE_LICENSE_URL,
        "filter_config": json.dumps(
            {
                "populated_places": {
                    "feature_class": POPULATED_PLACE_CLASS,
                    "min_population_exclusive": POPULATED_PLACE_MIN_POPULATION,
                    "admin_seat_codes": sorted(ADMIN_SEAT_CODES),
                },
                "terrain": {
                    "feature_class": TERRAIN_CLASS,
                    "feature_codes": sorted(SUPPORTED_TERRAIN_CODES),
                },
            },
            sort_keys=True,
            separators=(",", ":"),
        ),
        **{key: str(value) for key, value in counts.items()},
    }
    with connection:
        connection.executemany(
            "INSERT INTO metadata(key, value) VALUES (?, ?)",
            sorted(metadata.items()),
        )


def build_database(
    all_countries_zip: Path,
    admin1_codes: Path,
    output: Path,
    country_info: Path | None = None,
) -> dict[str, int]:
    admin1_names = load_admin1_names(admin1_codes)
    country_names = load_country_names(country_info)
    output.parent.mkdir(parents=True, exist_ok=True)
    fd, temp_name = tempfile.mkstemp(prefix=output.name + ".", suffix=".tmp", dir=output.parent)
    os.close(fd)
    temp_output = Path(temp_name)
    try:
        with sqlite3.connect(temp_output) as connection:
            create_schema(connection)
            row_id = 0

            def records() -> Iterable[PlaceRecord]:
                nonlocal row_id
                for row in iter_geonames_rows(all_countries_zip):
                    if not is_supported_row(row):
                        continue
                    row_id += 1
                    yield build_record(row_id, row, admin1_names, country_names)

            counts = insert_places(connection, records())
            write_metadata(connection, counts, all_countries_zip, admin1_codes, country_info)
            connection.execute("PRAGMA optimize")
            connection.execute("VACUUM")
        temp_output.replace(output)
        return counts
    except Exception:
        temp_output.unlink(missing_ok=True)
        raise


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--all-countries-zip", required=True, type=Path, help="Path to GeoNames allCountries.zip")
    parser.add_argument("--admin1-codes", required=True, type=Path, help="Path to GeoNames admin1CodesASCII.txt")
    parser.add_argument("--country-info", type=Path, help="Optional path to GeoNames countryInfo.txt")
    parser.add_argument("--output", required=True, type=Path, help="Output SQLite file")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    counts = build_database(
        all_countries_zip=args.all_countries_zip,
        admin1_codes=args.admin1_codes,
        country_info=args.country_info,
        output=args.output,
    )
    print(json.dumps({"output": str(args.output), **counts}, sort_keys=True))


if __name__ == "__main__":
    main()
