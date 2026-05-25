#!/usr/bin/env python3
"""GeoNames SQLite location-search sidecar for MTL Explorer."""

from __future__ import annotations

import json
import math
import os
import re
import sqlite3
import sys
import unicodedata
from dataclasses import dataclass
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any, Iterable
from urllib.parse import parse_qs, urlparse

try:
    import apsw  # type: ignore
except Exception:  # pragma: no cover - local tests can run without APSW.
    apsw = None

DEFAULT_DB_PATH = "/data/geonames-search.sqlite"
DEFAULT_HOST = "0.0.0.0"
DEFAULT_PORT = 8083
IMAGE_VERSION_FILE = Path("/opt/mtl/image-version")
IMAGE_BUILD_TIME_FILE = Path("/opt/mtl/image-build-time")
DEFAULT_LIMIT = 20
MAX_LIMIT = 50
MIN_SEARCH_QUERY_LENGTH = 2
IMPORTANCE_SORT = "importance"
DISTANCE_SORT = "distance"
READY_PHASE = "ready"
UNAVAILABLE_PHASE = "unavailable"
INVALID_REQUEST_PHASE = "invalid-request"
FORBIDDEN_PHASE = "forbidden"
ERROR_PHASE = "error"
AUTH_VERSION_PARAM = "mtl-version"
AUTH_SERVER_ID_PARAM = "mtl-server-id"
EARTH_RADIUS_METERS = 6_371_000.0
MIB = 1024 * 1024
DEFAULT_MMAP_SIZE = 256 * MIB
DEFAULT_CANDIDATE_LIMIT_MULTIPLIER = 12
DEFAULT_MIN_CANDIDATE_LIMIT = 120
DEFAULT_MAX_CANDIDATE_LIMIT = 600
DEFAULT_DISTANCE_CANDIDATE_LIMIT = 1000
SOURCE_ATTRIBUTION = "GeoNames"
SOURCE_URL = "https://www.geonames.org/"
SOURCE_LICENSE = "CC-BY 4.0"
SOURCE_LICENSE_URL = "https://creativecommons.org/licenses/by/4.0/"
PLACES_SOURCE_LAYER = "places"
POIS_SOURCE_LAYER = "pois"
TERRAIN_FEATURE_CLASS = "T"
DEFAULT_ADMIN1_LEVEL = 4
DEFAULT_MAX_ZOOM = 15

FEATURE_PRIORITY = {
    "capital": 0,
    "city": 1,
    "town": 2,
    "village": 3,
    "peak": 4,
    "mountain": 5,
    "pass": 6,
    "ridge": 7,
    "hill": 8,
    "neighbourhood": 9,
}

SEARCH_SQL = """
SELECT p.id,
       p.geoname_id,
       p.display_name,
       p.normalized_name,
       p.source_kind,
       p.feature_class,
       p.feature_code,
       p.kind,
       p.lat,
       p.lon,
       p.country_code,
       p.country_name,
       p.admin1_code,
       p.admin1_name,
       p.population,
       p.elevation,
       p.importance_score,
       CASE
         WHEN p.normalized_name = ? THEN 0.0
         ELSE bm25(place_names_fts)
       END AS fts_score,
       CASE
         WHEN p.normalized_name = ? THEN 0
         WHEN p.normalized_name LIKE ? ESCAPE '\\' THEN 1
         ELSE 2
       END AS match_rank
FROM place_names_fts
JOIN places p ON p.id = place_names_fts.rowid
WHERE place_names_fts MATCH ?
ORDER BY match_rank ASC,
         fts_score ASC,
         p.importance_score DESC,
         p.display_name COLLATE NOCASE ASC,
         p.geoname_id ASC
LIMIT ?
"""


@dataclass(frozen=True)
class SearchConfig:
    db_path: Path
    mmap_size: int = DEFAULT_MMAP_SIZE
    candidate_limit_multiplier: int = DEFAULT_CANDIDATE_LIMIT_MULTIPLIER
    min_candidate_limit: int = DEFAULT_MIN_CANDIDATE_LIMIT
    max_candidate_limit: int = DEFAULT_MAX_CANDIDATE_LIMIT
    distance_candidate_limit: int = DEFAULT_DISTANCE_CANDIDATE_LIMIT

    @classmethod
    def from_env(cls) -> "SearchConfig":
        return cls(
            db_path=Path(os.environ.get("MTL_LOCATION_SEARCH_DB", DEFAULT_DB_PATH)),
            mmap_size=env_int("MTL_LOCATION_SEARCH_MMAP_SIZE", DEFAULT_MMAP_SIZE),
            candidate_limit_multiplier=env_int(
                "MTL_LOCATION_SEARCH_CANDIDATE_LIMIT_MULTIPLIER",
                DEFAULT_CANDIDATE_LIMIT_MULTIPLIER,
            ),
            min_candidate_limit=env_int("MTL_LOCATION_SEARCH_MIN_CANDIDATE_LIMIT", DEFAULT_MIN_CANDIDATE_LIMIT),
            max_candidate_limit=env_int("MTL_LOCATION_SEARCH_MAX_CANDIDATE_LIMIT", DEFAULT_MAX_CANDIDATE_LIMIT),
            distance_candidate_limit=env_int("MTL_LOCATION_SEARCH_DISTANCE_CANDIDATE_LIMIT", DEFAULT_DISTANCE_CANDIDATE_LIMIT),
        )


@dataclass(frozen=True)
class Place:
    row_id: int
    geoname_id: int
    display_name: str
    normalized_name: str
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
    fts_score: float
    match_rank: int


@dataclass(frozen=True)
class RankedPlace:
    place: Place
    distance_meters: int | None


class SearchEngine:
    def __init__(self, config: SearchConfig):
        self.config = config

    def status(self) -> dict[str, Any]:
        db_path = self.config.db_path
        if not db_path.is_file():
            return unavailable_status(db_path, "GeoNames search database is missing.")
        if not os.access(db_path, os.R_OK):
            return unavailable_status(db_path, "GeoNames search database is not readable.")
        try:
            with self.connect() as connection:
                metadata = self.read_metadata(connection)
                row_count = int_metadata(metadata, "row_count", self.count(connection, "places"))
                populated_place_count = int_metadata(
                    metadata,
                    "populated_place_count",
                    self.count(connection, "places", "feature_class = 'P'"),
                )
                terrain_count = int_metadata(
                    metadata,
                    "terrain_count",
                    self.count(connection, "places", "feature_class = 'T'"),
                )
            return {
                "phase": READY_PHASE,
                "ready": True,
                "message": "GeoNames location search ready.",
                "db_path": str(db_path),
                "source_build_date": source_build_date(metadata),
                "source_attribution": metadata_value(metadata, "source_attribution", SOURCE_ATTRIBUTION),
                "source_url": metadata_value(metadata, "source_url", SOURCE_URL),
                "source_license": metadata_value(metadata, "source_license", SOURCE_LICENSE),
                "source_license_url": metadata_value(metadata, "source_license_url", SOURCE_LICENSE_URL),
                "sqlite_version": sqlite_runtime_version(),
                "row_count": row_count,
                "populated_place_count": populated_place_count,
                "terrain_count": terrain_count,
                "versionInfo": build_version_info(metadata),
            }
        except Exception as error:
            return unavailable_status(db_path, "GeoNames search database is unreadable.", detail=str(error))

    def search(self, raw_query: str, limit: int, sort: str, lat: float | None, lon: float | None) -> dict[str, Any]:
        query = raw_query.strip()
        normalized = normalize_query(query)
        if len(normalized) < MIN_SEARCH_QUERY_LENGTH:
            return response(
                query=query,
                normalized_query=normalized,
                limit=limit,
                sort=sort,
                ready=False,
                phase=INVALID_REQUEST_PHASE,
                message=f"Query must contain at least {MIN_SEARCH_QUERY_LENGTH} searchable characters.",
                results=[],
            )

        status = self.status()
        if not status.get("ready"):
            return response(
                query=query,
                normalized_query=normalized,
                limit=limit,
                sort=sort,
                ready=False,
                phase=status.get("phase", UNAVAILABLE_PHASE),
                message=status.get("message", "Location search is unavailable."),
                results=[],
            )

        effective_sort = normalize_sort(sort, lat, lon)
        try:
            candidates = self.query_candidates(normalized, effective_sort, limit)
            ranked = [RankedPlace(place, distance_for(place, lat, lon)) for place in candidates]
            ranked.sort(key=distance_rank_key if effective_sort == DISTANCE_SORT else importance_rank_key)
            return response(
                query=query,
                normalized_query=normalized,
                limit=limit,
                sort=effective_sort,
                ready=True,
                phase=READY_PHASE,
                message="",
                results=[to_result(item) for item in ranked[:limit]],
            )
        except Exception as error:
            print(f"[location-search] query failed: {error}", file=sys.stderr, flush=True)
            return response(
                query=query,
                normalized_query=normalized,
                limit=limit,
                sort=effective_sort,
                ready=False,
                phase=ERROR_PHASE,
                message="Location search query failed.",
                results=[],
            )

    def query_candidates(self, normalized_query: str, sort: str, limit: int) -> list[Place]:
        params = (
            normalized_query,
            normalized_query,
            escape_like(normalized_query) + "%",
            to_prefix_fts_query(normalized_query),
            candidate_limit(limit, sort, self.config),
        )
        with self.connect() as connection:
            rows = execute_rows(connection, SEARCH_SQL, params)
            return [place_from_row(row) for row in rows]

    def connect(self):
        db_uri = self.config.db_path.absolute().as_uri()
        if apsw is not None:
            connection = apsw.Connection(
                db_uri,
                flags=apsw.SQLITE_OPEN_READONLY | apsw.SQLITE_OPEN_URI,
            )
            connection.execute("PRAGMA query_only = true")
            connection.execute(f"PRAGMA mmap_size = {max(0, self.config.mmap_size)}")
            return ApswConnectionContext(connection)
        connection = sqlite3.connect(f"{db_uri}?mode=ro", uri=True)
        connection.execute("PRAGMA query_only = true")
        connection.execute(f"PRAGMA mmap_size = {max(0, self.config.mmap_size)}")
        return SqliteConnectionContext(connection)

    @staticmethod
    def read_metadata(connection: Any) -> dict[str, str]:
        return dict(execute_rows(connection, "SELECT key, value FROM metadata ORDER BY key"))

    @staticmethod
    def count(connection: Any, table: str, where_clause: str | None = None) -> int:
        sql = f"SELECT COUNT(*) FROM {table}" + (f" WHERE {where_clause}" if where_clause else "")
        rows = execute_rows(connection, sql)
        return int(rows[0][0]) if rows else 0


class ApswConnectionContext:
    def __init__(self, connection: Any):
        self.connection = connection

    def __enter__(self):
        return self.connection

    def __exit__(self, exc_type, exc, traceback):
        self.connection.close()


class SqliteConnectionContext:
    def __init__(self, connection: sqlite3.Connection):
        self.connection = connection

    def __enter__(self):
        return self.connection

    def __exit__(self, exc_type, exc, traceback):
        self.connection.close()


class LocationSearchHandler(BaseHTTPRequestHandler):
    engine: SearchEngine

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        params = parse_qs(parsed.query)
        if parsed.path == "/health":
            self.send_text(HTTPStatus.OK, "ok")
            return
        if parsed.path == "/status":
            if not self.is_authorized(params, parsed.path):
                return
            self.log_sidecar_access(parsed.path, params)
            self.send_json(HTTPStatus.OK, self.engine.status())
            return
        if parsed.path == "/search":
            if not self.is_authorized(params, parsed.path):
                return
            query = first_param(params, "q")
            limit = clamp_limit(first_int_param(params, "limit", DEFAULT_LIMIT))
            sort = first_param(params, "sort", IMPORTANCE_SORT)
            lat = first_float_param(params, "lat")
            lon = first_float_param(params, "lon")
            self.log_sidecar_access(parsed.path, params, query=query, limit=limit, sort=sort)
            self.send_json(HTTPStatus.OK, self.engine.search(query, limit, sort, lat, lon))
            return
        self.send_json(HTTPStatus.NOT_FOUND, {"message": "Not found"})

    def is_authorized(self, params: dict[str, list[str]], path: str) -> bool:
        version = first_param(params, AUTH_VERSION_PARAM)
        server_id = first_param(params, AUTH_SERVER_ID_PARAM)
        if version.strip() and server_id.strip():
            return True
        print(
            "[location-search] forbidden "
            f"path={path} remote={self.client_address[0]} "
            f"{AUTH_VERSION_PARAM}={display_log_value(version)} "
            f"{AUTH_SERVER_ID_PARAM}={display_log_value(server_id)}",
            flush=True,
        )
        self.send_json(HTTPStatus.FORBIDDEN, forbidden_status())
        return False

    def log_sidecar_access(
        self,
        path: str,
        params: dict[str, list[str]],
        *,
        query: str | None = None,
        limit: int | None = None,
        sort: str | None = None,
    ) -> None:
        message = (
            "[location-search] request "
            f"path={path} remote={self.client_address[0]} "
            f"{AUTH_VERSION_PARAM}={display_log_value(first_param(params, AUTH_VERSION_PARAM))} "
            f"{AUTH_SERVER_ID_PARAM}={display_log_value(first_param(params, AUTH_SERVER_ID_PARAM))}"
        )
        if query is not None:
            message += f" q={display_log_value(query)} limit={limit} sort={display_log_value(sort)}"
        print(message, flush=True)

    def send_json(self, status: HTTPStatus, payload: dict[str, Any]) -> None:
        body = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store, no-cache, must-revalidate")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def send_text(self, status: HTTPStatus, payload: str) -> None:
        body = payload.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt: str, *args: Any) -> None:
        print(f"[location-search] {self.address_string()} {fmt % args}", flush=True)


def execute_rows(connection: Any, sql: str, params: Iterable[Any] = ()) -> list[tuple[Any, ...]]:
    return list(connection.execute(sql, tuple(params)))


def place_from_row(row: tuple[Any, ...]) -> Place:
    return Place(
        row_id=int(row[0]),
        geoname_id=int(row[1]),
        display_name=row[2],
        normalized_name=row[3],
        source_kind=row[4],
        feature_class=row[5],
        feature_code=row[6],
        kind=row[7],
        lat=float(row[8]),
        lon=float(row[9]),
        country_code=row[10],
        country_name=row[11],
        admin1_code=row[12],
        admin1_name=row[13],
        population=None if row[14] is None else int(row[14]),
        elevation=None if row[15] is None else int(row[15]),
        importance_score=float(row[16]),
        fts_score=float(row[17]),
        match_rank=int(row[18]),
    )


def normalize_query(value: str) -> str:
    decomposed = unicodedata.normalize("NFKD", value)
    without_marks = "".join(ch for ch in decomposed if not unicodedata.combining(ch))
    lowered = without_marks.lower()
    cleaned = "".join(ch if ch.isalnum() else " " for ch in lowered)
    return re.sub(r"\s+", " ", cleaned).strip()


def to_prefix_fts_query(normalized_query: str) -> str:
    return " AND ".join(f"{token}*" for token in normalized_query.split() if token)


def escape_like(value: str) -> str:
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")


def normalize_sort(sort: str, lat: float | None, lon: float | None) -> str:
    normalized = (sort or IMPORTANCE_SORT).strip().lower()
    return DISTANCE_SORT if normalized == DISTANCE_SORT and valid_coordinate(lat, lon) else IMPORTANCE_SORT


def candidate_limit(limit: int, sort: str, config: SearchConfig) -> int:
    if sort == DISTANCE_SORT:
        return config.distance_candidate_limit
    return max(
        config.min_candidate_limit,
        min(limit * config.candidate_limit_multiplier, config.max_candidate_limit),
    )


def distance_for(place: Place, lat: float | None, lon: float | None) -> int | None:
    if not valid_coordinate(lat, lon):
        return None
    return round(distance_meters(lat, lon, place.lat, place.lon))


def distance_meters(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    lat1_rad = math.radians(lat1)
    lat2_rad = math.radians(lat2)
    d_lat = math.radians(lat2 - lat1)
    d_lon = math.radians(lon2 - lon1)
    a = math.sin(d_lat / 2.0) ** 2 + math.cos(lat1_rad) * math.cos(lat2_rad) * math.sin(d_lon / 2.0) ** 2
    return EARTH_RADIUS_METERS * 2.0 * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))


def valid_coordinate(lat: float | None, lon: float | None) -> bool:
    return (
        lat is not None
        and lon is not None
        and math.isfinite(lat)
        and math.isfinite(lon)
        and -90 <= lat <= 90
        and -180 <= lon <= 180
    )


def importance_rank_key(item: RankedPlace) -> tuple[Any, ...]:
    place = item.place
    return (
        place.match_rank,
        place.fts_score,
        FEATURE_PRIORITY.get(place.kind, 999),
        -(place.population or 0),
        -((place.elevation or 0) if place.feature_class == TERRAIN_FEATURE_CLASS else 0),
        place.display_name.casefold(),
        place.geoname_id,
    )


def distance_rank_key(item: RankedPlace) -> tuple[Any, ...]:
    distance = item.distance_meters if item.distance_meters is not None else sys.maxsize
    return (distance, *importance_rank_key(item))


def to_result(item: RankedPlace) -> dict[str, Any]:
    place = item.place
    return {
        "display_name": place.display_name,
        "source_layer": POIS_SOURCE_LAYER if place.feature_class == TERRAIN_FEATURE_CLASS else PLACES_SOURCE_LAYER,
        "kind": place.kind,
        "kind_detail": place.kind,
        "min_zoom": min_zoom(place),
        "max_zoom": DEFAULT_MAX_ZOOM,
        "lat": place.lat,
        "lon": place.lon,
        "distance_meters": item.distance_meters,
        "country_code": place.country_code,
        "country_name": place.country_name,
        "admin1_code": place.admin1_code,
        "admin1_name": place.admin1_name,
        "admin1_level": DEFAULT_ADMIN1_LEVEL,
        "lang": "local",
        "name": place.display_name,
    }


def min_zoom(place: Place) -> int:
    if place.kind == "capital":
        return 4
    if place.kind == "city":
        return 7
    if place.kind == "town":
        return 9
    if place.kind in {"village", "neighbourhood"}:
        return 11
    return 12


def response(
    *,
    query: str,
    normalized_query: str,
    limit: int,
    sort: str,
    ready: bool,
    phase: str,
    message: str,
    results: list[dict[str, Any]],
) -> dict[str, Any]:
    return {
        "query": query,
        "normalized_query": normalized_query,
        "limit": limit,
        "sort": sort,
        "ready": ready,
        "phase": phase,
        "message": message,
        "results": results,
    }


def unavailable_status(db_path: Path, message: str, detail: str | None = None) -> dict[str, Any]:
    if detail:
        print(f"[location-search] status unavailable: {detail}", file=sys.stderr, flush=True)
    return {
        "phase": UNAVAILABLE_PHASE,
        "ready": False,
        "message": message,
        "db_path": str(db_path),
        "source_attribution": SOURCE_ATTRIBUTION,
        "source_url": SOURCE_URL,
        "source_license": SOURCE_LICENSE,
        "source_license_url": SOURCE_LICENSE_URL,
        "sqlite_version": sqlite_runtime_version(),
        "row_count": 0,
        "populated_place_count": 0,
        "terrain_count": 0,
        "versionInfo": build_version_info(),
    }


def forbidden_status() -> dict[str, Any]:
    return {
        "phase": FORBIDDEN_PHASE,
        "ready": False,
        "message": "Location search requires backend identity parameters.",
        "versionInfo": build_version_info(),
    }


def sqlite_runtime_version() -> str:
    if apsw is not None:
        return apsw.sqlitelibversion()
    return sqlite3.sqlite_version


def apsw_runtime_version() -> str | None:
    if apsw is None:
        return None
    version_fn = getattr(apsw, "apswversion", None)
    if version_fn is None:
        return None
    return str(version_fn())


def build_version_info(metadata: dict[str, str] | None = None) -> dict[str, Any]:
    metadata = metadata or {}
    components = {
        "python": sys.version.split()[0],
        "sqlite": sqlite_runtime_version(),
    }
    apsw_version = apsw_runtime_version()
    if apsw_version:
        components["apsw"] = apsw_version

    data = {
        "sourceAttribution": metadata_value(metadata, "source_attribution", SOURCE_ATTRIBUTION),
        "sourceUrl": metadata_value(metadata, "source_url", SOURCE_URL),
        "sourceLicense": metadata_value(metadata, "source_license", SOURCE_LICENSE),
        "sourceLicenseUrl": metadata_value(metadata, "source_license_url", SOURCE_LICENSE_URL),
    }
    optional_data = {
        "sourceBuildDate": source_build_date(metadata),
        "schemaVersion": metadata_value(metadata, "schema_version"),
        "rowCount": metadata_value(metadata, "row_count"),
        "populatedPlaceCount": metadata_value(metadata, "populated_place_count"),
        "terrainCount": metadata_value(metadata, "terrain_count"),
    }
    data.update({key: value for key, value in optional_data.items() if value})

    return {
        "image": {
            "version": metadata_file_value("MTL_IMAGE_VERSION", IMAGE_VERSION_FILE),
            "buildTime": metadata_file_value("MTL_IMAGE_BUILD_TIME", IMAGE_BUILD_TIME_FILE),
        },
        "components": components,
        "data": data,
    }


def metadata_file_value(env_name: str, file_path: Path) -> str | None:
    value = os.environ.get(env_name, "").strip()
    if value:
        return value
    try:
        file_value = file_path.read_text(encoding="utf-8").strip()
        return file_value or None
    except OSError:
        return None


def metadata_value(metadata: dict[str, str], key: str, fallback: str | None = None) -> str | None:
    value = metadata.get(key)
    return value if value else fallback


def source_build_date(metadata: dict[str, str]) -> str | None:
    return metadata_value(metadata, "build_time") or metadata_value(metadata, "source_all_countries_timestamp")


def int_metadata(metadata: dict[str, str], key: str, fallback: int) -> int:
    try:
        return int(metadata.get(key, ""))
    except ValueError:
        return fallback


def first_param(params: dict[str, list[str]], key: str, default: str = "") -> str:
    values = params.get(key)
    return values[0] if values else default


def first_int_param(params: dict[str, list[str]], key: str, default: int) -> int:
    try:
        return int(first_param(params, key, str(default)))
    except ValueError:
        return default


def first_float_param(params: dict[str, list[str]], key: str) -> float | None:
    try:
        raw = first_param(params, key)
        return float(raw) if raw else None
    except ValueError:
        return None


def clamp_limit(limit: int) -> int:
    return max(1, min(limit, MAX_LIMIT))


def env_int(name: str, default: int) -> int:
    try:
        return int(os.environ.get(name, str(default)))
    except ValueError:
        return default


def display_log_value(value: Any) -> str:
    text = "" if value is None else str(value).strip()
    if not text:
        return "-"
    return json.dumps(text[:160], ensure_ascii=False)


def version_image_field(status: dict[str, Any], key: str) -> str | None:
    version_info = status.get("versionInfo")
    if not isinstance(version_info, dict):
        return None
    image = version_info.get("image")
    if not isinstance(image, dict):
        return None
    value = image.get(key)
    return str(value) if value else None


def main() -> None:
    config = SearchConfig.from_env()
    engine = SearchEngine(config)
    LocationSearchHandler.engine = engine
    host = os.environ.get("HOST", DEFAULT_HOST)
    port = env_int("PORT", DEFAULT_PORT)
    status = engine.status()
    print(
        "[location-search] startup "
        f"host={host} port={port} db={config.db_path} "
        f"ready={status.get('ready')} phase={display_log_value(status.get('phase'))} "
        f"imageVersion={display_log_value(version_image_field(status, 'version'))} "
        f"imageBuildTime={display_log_value(version_image_field(status, 'buildTime'))} "
        f"sqlite={display_log_value(sqlite_runtime_version())} "
        f"message={display_log_value(status.get('message'))}",
        flush=True,
    )
    ThreadingHTTPServer((host, port), LocationSearchHandler).serve_forever()


if __name__ == "__main__":
    main()
