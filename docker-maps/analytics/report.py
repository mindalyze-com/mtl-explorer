#!/usr/bin/env python3
"""Stream map access logs into private, atomically published static reports."""
from __future__ import annotations

import argparse
import bisect
import calendar
import collections
import contextlib
import datetime as dt
import fcntl
import gzip
import heapq
import html
import itertools
import json
import logging
import logging.handlers
import math
import os
from pathlib import Path
import re
import resource
import shutil
import struct
import sys
import tempfile
import time
import urllib.parse
import uuid
from dataclasses import dataclass, field
from zoneinfo import ZoneInfo

UTC = dt.timezone.utc
ROOT = Path(__file__).resolve().parent
DEFAULT_OUTPUT = Path('/app/analytics/data')
DEFAULT_LOGS = Path('/data/logs')
CATEGORIES = ('candidate', 'demo', 'internal', 'unclassified')
GROUPS = ('hour', 'day', 'week', 'month')
# New installations use 10-character ids; older installations keep their 22-character id.
STANDARD_ID = re.compile(r'[1-9A-HJ-NP-Za-km-z]{10}(?:[1-9A-HJ-NP-Za-km-z]{12})?\Z')
FIELDS = re.compile(r'(\w+)=("(?:\\.|[^"\\])*"|\S+)')
KNOWN_ID = re.compile(r'(?:"?serverId"?\s*[:=]\s*"?|server_id:)([A-Za-z0-9_-]{10,64})')
MAX_LINE = 65536
MAX_IDENTITIES = 100000
MAX_DIMENSIONS = 256
MAX_BUCKETS = 10000
MAX_HOURS = 100000
MAX_LOAD_DAYS = 10000
RATE_CHUNK_KEYS = 50000
RATE_MERGE_FILES = 32
RATE_RECORD = struct.Struct('!qIQ')
LOG_BYTES = 1024 * 1024
LATENCY_BOUNDS = (0, 1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384, 32768, 65536, math.inf)
LOGGER = logging.getLogger('mtl.map_reports')
PRESETS = (
    ('last-week-hourly', 'Last week · hourly', 7, 'hour'),
    ('last-week-daily', 'Last week · daily', 7, 'day'),
    ('last-month-hourly', 'Last month · hourly', 30, 'hour'),
    ('last-three-months-daily', 'Last 3 months · daily', 90, 'day'),
    ('last-year-daily', 'Last year · daily', 365, 'day'),
    ('last-year-weekly', 'Last year · weekly', 365, 'week'),
)


def configure_logging(root: Path, level: str = 'INFO') -> None:
    private = root / 'private'
    private.mkdir(parents=True, exist_ok=True, mode=0o750)
    for handler in LOGGER.handlers:
        handler.close()
    LOGGER.handlers.clear()
    LOGGER.setLevel(level)
    LOGGER.propagate = False
    formatter = logging.Formatter('%(asctime)sZ %(levelname)s %(name)s %(message)s', '%Y-%m-%dT%H:%M:%S')
    formatter.converter = time.gmtime
    log_path = private / 'report.log'
    handler = logging.handlers.RotatingFileHandler(log_path, maxBytes=LOG_BYTES, backupCount=5, encoding='utf-8')
    log_path.chmod(0o600)
    console = logging.StreamHandler()
    for sink in (handler, console):
        sink.setFormatter(formatter)
        LOGGER.addHandler(sink)


@contextlib.contextmanager
def run_lock(path: Path, operation: str = 'Report run'):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('a') as handle:
        path.chmod(0o600)
        try:
            fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            LOGGER.info('%s skipped: another job holds the shared lock', operation)
            yield False
            return
        try:
            yield True
        finally:
            fcntl.flock(handle, fcntl.LOCK_UN)


def atomic_json(path: Path, data: dict, mode: int = 0o600) -> None:
    temporary = path.with_name(path.name + '.' + uuid.uuid4().hex + '.tmp')
    try:
        with temporary.open('w', encoding='utf-8') as handle:
            json.dump(data, handle, indent=2, allow_nan=False)
            handle.write('\n')
            handle.flush()
            os.fsync(handle.fileno())
        temporary.chmod(mode)
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def parse_date(value: str, zone: ZoneInfo) -> dt.datetime:
    parsed = dt.datetime.fromisoformat(value.replace('Z', '+00:00'))
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=zone)
    return parsed.astimezone(UTC)


def bucket_start(instant: dt.datetime, group: str, zone: ZoneInfo) -> dt.datetime:
    local = instant.astimezone(zone)
    if group == 'hour':
        return local.replace(minute=0, second=0, microsecond=0).astimezone(UTC)
    date = local.date()
    if group == 'week':
        date -= dt.timedelta(days=date.weekday())
    elif group == 'month':
        date = date.replace(day=1)
    return dt.datetime.combine(date, dt.time(), zone).astimezone(UTC)


def next_bucket(start: dt.datetime, group: str, zone: ZoneInfo) -> dt.datetime:
    if group == 'hour':
        candidate = start + dt.timedelta(hours=1)
        following = bucket_start(candidate, group, zone)
        # A partial-hour DST rollback can map back into the previous local hour.
        while following <= start:
            candidate += dt.timedelta(minutes=1)
            following = bucket_start(candidate, group, zone)
        return following
    local = start.astimezone(zone)
    if group == 'month':
        year, month = local.year + (local.month == 12), local.month % 12 + 1
        date = dt.date(year, month, 1)
    else:
        date = local.date() + dt.timedelta(days=7 if group == 'week' else 1)
    return dt.datetime.combine(date, dt.time(), zone).astimezone(UTC)


def recent_start(end: dt.datetime, days: int, zone: ZoneInfo) -> dt.datetime:
    # Calendar days retain local clock time across daylight-saving transitions.
    return (end.astimezone(zone) - dt.timedelta(days=days)).astimezone(UTC)


def recent_month_start(end: dt.datetime, months: int, zone: ZoneInfo) -> dt.datetime:
    local = end.astimezone(zone)
    serial = local.year * 12 + local.month - 1 - months
    year, month = serial // 12, serial % 12 + 1
    return local.replace(year=year, month=month, day=min(local.day, calendar.monthrange(year, month)[1])).astimezone(UTC)


@dataclass(slots=True)
class Record:
    timestamp: dt.datetime
    path: str
    status: int
    size: int
    duration_ms: float
    server_id: str = ''
    login_ref: str = ''
    instance_type: str = ''
    version: str = ''
    build: str = ''
    request_range: bool = False
    response_range: bool = False
    forwarded_ip: bool = False


def field_text(value: str) -> str:
    if value.startswith('"'):
        value = value[1:-1]
        # Nginx's default escaping is hexadecimal, rather than JSON escaping.
        value = re.sub(r'\\x([0-9a-fA-F]{2})', lambda m: chr(int(m[1], 16)), value)
        value = value.replace('\\"', '"').replace('\\\\', '\\')
    return value


def parse_record(line: str) -> Record:
    if line.startswith('{'):
        entry = json.loads(line)
        request = entry['request']
        timestamp = dt.datetime.fromtimestamp(float(entry['ts']), UTC)
        target = request['uri']
        status, size = int(entry['status']), int(entry.get('size', 0))
        duration = float(entry.get('duration', 0)) * 1000
        headers = {k.lower(): v for k, v in request.get('headers', {}).items()}
        response = {k.lower(): v for k, v in entry.get('resp_headers', {}).items()}
        values = {}
        requested_range = bool(headers.get('range'))
        returned_range = bool(response.get('content-range'))
        forwarded = bool(headers.get('x-forwarded-for') or headers.get('x-real-ip'))
    else:
        timestamp = parse_date(line.split(' ', 1)[0], ZoneInfo('UTC'))
        values = {match[1]: field_text(match[2]) for match in FIELDS.finditer(line)}
        request = values['request'].split(' ', 2)
        if len(request) < 2:
            raise ValueError('Missing request target')
        target = request[1]
        status, size = int(values['status']), int(values['bytes'])
        duration = float(values['request_time']) * 1000
        requested_range = values.get('request_range', '') not in ('', '-')
        returned_range = values.get('response_content_range', '') not in ('', '-')
        forwarded = any(values.get(k, '') not in ('', '-') for k in ('forwarded_for', 'real_ip', 'client_ip'))
    if size < 0 or not 0 <= status <= 599 or not math.isfinite(duration) or duration < 0:
        raise ValueError('Invalid status, response size or duration')
    uri = urllib.parse.urlsplit(target)
    query = urllib.parse.parse_qs(uri.query)

    def metadata(field_name, parameter, legacy=''):
        result = values.get(field_name) or query.get(parameter, [''])[0]
        if not result and legacy:
            result = values.get('mtl_user_session_id') or query.get(legacy, [''])[0]
        return urllib.parse.unquote(result)[:128]

    return Record(timestamp, uri.path, status, size, duration,
                  metadata('mtl_server_id', 'mtl-server-id'),
                  metadata('mtl_client_login_ref', 'mtl-client-login-ref', 'mtl-user-session-id'),
                  metadata('mtl_instance_type', 'mtl-instance-type'),
                  metadata('mtl_version', 'mtl-version'), metadata('mtl_build', 'mtl-build'),
                  requested_range, returned_range, forwarded)


def read_logs(log_dir: Path, pattern: str, diagnostics: collections.Counter):
    files = sorted(p for p in log_dir.glob(pattern) if p.is_file())
    LOGGER.info('Scanning access logs: files=%d pattern=%s', len(files), pattern)
    progress_at = time.monotonic()
    for path in files:
        before = path.stat()
        compressed = path.suffix == '.gz'
        diagnostics['files'] += 1
        diagnostics['compressed_files' if compressed else 'plain_files'] += 1
        diagnostics['stored_bytes'] += before.st_size
        with (gzip.open(path, 'rb') if compressed else path.open('rb')) as handle:
            consumed = 0
            incomplete_tail = False
            while True:
                limit = MAX_LINE + 1 if compressed else min(MAX_LINE + 1, before.st_size - consumed)
                if limit <= 0:
                    break
                line = handle.readline(limit)
                if not line:
                    break
                consumed += len(line)
                diagnostics['read_bytes'] += len(line)
                if len(line) > MAX_LINE:
                    raise ValueError(f'Oversized access-log record in {path.name}')
                if not line.endswith(b'\n'):
                    if compressed:
                        raise ValueError(f'Incomplete compressed access-log record in {path.name}')
                    diagnostics['incomplete_tail_records'] += 1
                    incomplete_tail = True
                    break
                diagnostics['lines'] += 1
                try:
                    record = parse_record(line.decode('utf-8'))
                except (ValueError, KeyError, TypeError, OverflowError, UnicodeError) as error:
                    diagnostics['parse_errors'] += 1
                    if diagnostics['parse_errors'] <= 3:
                        LOGGER.warning('Access-log parse failed: file=%s line=%d error=%s', path.name, diagnostics['lines'], type(error).__name__)
                    continue
                diagnostics['parsed_records'] += 1
                diagnostics['caddy_records' if line.startswith(b'{') else 'nginx_records'] += 1
                yield record
                if time.monotonic() - progress_at >= 30:
                    LOGGER.info('Scan progress: files=%d records=%d errors=%d', diagnostics['files'], diagnostics['parsed_records'], diagnostics['parse_errors'])
                    progress_at = time.monotonic()
            if not compressed and (path.stat().st_ino != before.st_ino or path.stat().st_size < before.st_size or consumed < before.st_size and not incomplete_tail):
                raise RuntimeError(f'Access log changed during scan: {path.name}; previous reports retained')
        LOGGER.debug('Access-log file completed: %s', path.name)


def load_classifications(path: Path | None, known_roots: list[Path]) -> dict[str, str]:
    result = {}
    if path:
        if not path.is_file():
            raise FileNotFoundError(f'Classification file does not exist: {path}')
        document = json.loads(path.read_text())
        for sid, value in document.get('installations', document).items():
            category = value.get('category') if isinstance(value, dict) else value
            if category not in ('demo', 'internal', 'candidate'):
                raise ValueError('Classification category must be demo, internal or candidate')
            result[sid] = category
    for root in known_roots:
        if not root.exists():
            raise FileNotFoundError(f'Known-ID source does not exist: {root}')
        files = [root] if root.is_file() else root.rglob('*')
        for path in files:
            if path.is_file() and path.suffix in ('.md', '.txt', '.log', '.json', '.yml', '.yaml') and path.stat().st_size <= 4000000:
                for sid in KNOWN_ID.findall(path.read_text(errors='replace')):
                    result.setdefault(sid, 'internal')
    return result


def category_for(record: Record, classifications: dict[str, str]) -> str:
    if record.server_id in classifications:
        return classifications[record.server_id]
    if record.instance_type == 'demo':
        return 'demo'
    return 'candidate' if STANDARD_ID.fullmatch(record.server_id) else 'unclassified'


@dataclass
class Bucket:
    requests: int = 0
    size: int = 0
    statuses: collections.Counter = field(default_factory=collections.Counter)
    ids: set = field(default_factory=set)
    logins: set = field(default_factory=set)
    coverage: collections.Counter = field(default_factory=collections.Counter)
    latency: collections.Counter = field(default_factory=collections.Counter)
    types: collections.Counter = field(default_factory=collections.Counter)

    def add(self, record: Record):
        self.requests += 1
        self.size += record.size
        self.statuses[str(record.status)] += 1
        if 200 <= record.status < 400 and STANDARD_ID.fullmatch(record.server_id):
            self.ids.add(record.server_id)
            if record.login_ref:
                self.logins.add((record.server_id, record.login_ref))
        self.coverage['login_ref'] += bool(record.login_ref)
        self.coverage['instance_type'] += record.instance_type in ('demo', 'standard')
        self.coverage['version'] += bool(record.version)
        self.coverage['build'] += bool(record.build)
        self.coverage['request_range'] += record.request_range
        self.coverage['response_range'] += record.response_range
        self.coverage['forwarded_ip'] += record.forwarded_ip
        self.types[record.instance_type if record.instance_type in ('demo', 'standard') else 'unknown'] += 1
        index = min(bisect.bisect_left(LATENCY_BOUNDS, record.duration_ms), len(LATENCY_BOUNDS) - 1)
        self.latency[index] += 1

    def serialise(self):
        estimates = {}
        for label, fraction in (('p50', .5), ('p95', .95), ('p99', .99)):
            accumulated = 0
            estimates[label] = None
            for index, count in sorted(self.latency.items()):
                accumulated += count
                if accumulated >= math.ceil(self.requests * fraction):
                    estimates[label] = LATENCY_BOUNDS[index] if math.isfinite(LATENCY_BOUNDS[index]) else None
                    break
        return {'requests': self.requests, 'bytes': self.size, 'installations': len(self.ids), 'client_logins': len(self.logins),
                'statuses': dict(self.statuses), 'instance_types': dict(self.types), 'coverage': dict(self.coverage),
                'coverage_pct': {key: round(100 * count / self.requests, 3) if self.requests else 0 for key, count in self.coverage.items()},
                'latency_ms_upper_estimates': estimates}


@dataclass
class Hour:
    requests: int = 0
    candidate_ids: set = field(default_factory=set)
    candidate_logins: set = field(default_factory=set)


class SecondCounts:
    """Spill timestamp counters, then merge; never retain all request timestamps."""

    def __init__(self, directory: Path):
        self.directory = directory
        self.counts = collections.Counter()
        self.runs = []
        self.number = 0

    def new_path(self):
        self.number += 1
        return self.directory / str(self.number)

    def add(self, timestamp: dt.datetime, mask: int):
        if mask:
            self.counts[(math.floor(timestamp.timestamp()), mask)] += 1
            if len(self.counts) >= RATE_CHUNK_KEYS:
                self.flush()

    def flush(self):
        if not self.counts:
            return
        path = self.new_path()
        with path.open('wb') as handle:
            for (second, mask), count in sorted(self.counts.items()):
                handle.write(RATE_RECORD.pack(second, mask, count))
        self.runs.append(path)
        self.counts.clear()

    @staticmethod
    def merge(paths):
        with contextlib.ExitStack() as stack:
            streams = []
            for path in paths:
                handle = stack.enter_context(path.open('rb'))
                streams.append(map(RATE_RECORD.unpack, iter(lambda handle=handle: handle.read(RATE_RECORD.size), b'')))
            for key, rows in itertools.groupby(heapq.merge(*streams, key=lambda row: row[:2]), key=lambda row: row[:2]):
                yield *key, sum(row[2] for row in rows)

    def finish(self, scopes):
        self.flush()
        initial_runs = len(self.runs)
        while len(self.runs) > RATE_MERGE_FILES:
            merged = []
            for offset in range(0, len(self.runs), RATE_MERGE_FILES):
                paths = self.runs[offset:offset + RATE_MERGE_FILES]
                destination = self.new_path()
                with destination.open('wb') as handle:
                    for row in self.merge(paths):
                        handle.write(RATE_RECORD.pack(*row))
                for path in paths:
                    path.unlink()
                merged.append(destination)
            self.runs = merged
        for second, rows in itertools.groupby(self.merge(self.runs), key=lambda row: row[0]):
            counts = [0] * len(scopes)
            for _, mask, count in rows:
                for index in range(len(scopes)):
                    if mask & (1 << index):
                        counts[index] += count
            timestamp = dt.datetime.fromtimestamp(second, UTC)
            for scope, count in zip(scopes, counts):
                if count:
                    scope.add_second(timestamp, count)
        LOGGER.info('Exact second peaks completed: sorted_runs=%d', initial_runs)


@dataclass
class Scope:
    name: str
    title: str
    start: dt.datetime | None
    end: dt.datetime
    group: str
    zone: ZoneInfo
    load_enabled: bool = True
    totals: dict = field(default_factory=lambda: {category: Bucket() for category in ('all',) + CATEGORIES})
    buckets: dict = field(default_factory=dict)
    versions: dict = field(default_factory=dict)
    candidate_counts: collections.Counter = field(default_factory=collections.Counter)
    candidate_dates: dict = field(default_factory=lambda: collections.defaultdict(set))
    hours: dict = field(default_factory=dict)
    second_peaks: dict = field(default_factory=dict)
    day_second_peaks: dict = field(default_factory=dict)
    day_totals: dict = field(default_factory=dict)

    def add(self, record: Record, category: str):
        if (self.start is not None and record.timestamp < self.start) or record.timestamp >= self.end:
            return False
        key = bucket_start(record.timestamp, self.group, self.zone)
        if key not in self.buckets:
            if len(self.buckets) >= MAX_BUCKETS:
                raise ValueError('Too many buckets; choose a coarser grouping or shorter range')
            self.buckets[key] = {name: Bucket() for name in ('all',) + CATEGORIES}
        for name in ('all', category):
            self.totals[name].add(record)
            self.buckets[key][name].add(record)
        hour = None
        if self.load_enabled:
            hour_key = bucket_start(record.timestamp, 'hour', self.zone)
            if hour_key not in self.hours:
                if len(self.hours) >= MAX_HOURS:
                    raise ValueError('Too many active hours; choose a shorter date range')
                self.hours[hour_key] = Hour()
            hour = self.hours[hour_key]
            hour.requests += 1
            day = bucket_start(record.timestamp, 'day', self.zone)
            if day not in self.day_totals:
                self.day_totals[day] = {'requests': 0, 'bytes': 0}
            self.day_totals[day]['requests'] += 1
            self.day_totals[day]['bytes'] += record.size
        if category == 'candidate' and 200 <= record.status < 400 and STANDARD_ID.fullmatch(record.server_id):
            self.candidate_counts[record.server_id] += 1
            self.candidate_dates[record.server_id].add(record.timestamp.astimezone(self.zone).date())
            if hour is not None:
                hour.candidate_ids.add(record.server_id)
                if record.login_ref:
                    hour.candidate_logins.add((record.server_id, record.login_ref))
        version = record.version or 'unknown'
        if version not in self.versions and len(self.versions) >= MAX_DIMENSIONS:
            version = 'other'
        item = self.versions.setdefault(version, {'requests': 0, 'ids': set(), 'builds': set()})
        item['requests'] += 1
        if STANDARD_ID.fullmatch(record.server_id):
            item['ids'].add(record.server_id)
        if record.build and len(item['builds']) < MAX_DIMENSIONS:
            item['builds'].add(record.build)
        return True

    def add_second(self, timestamp: dt.datetime, count: int):
        for group, peaks in ((self.group, self.second_peaks), ('day', self.day_second_peaks)):
            key = bucket_start(timestamp, group, self.zone)
            previous = peaks.get(key)
            if previous is None or count > previous['requests']:
                peaks[key] = {'at_utc': timestamp.isoformat(), 'requests': count}

    def hour_peaks(self, start, group=None):
        peaks = {}
        for timestamp, hour in sorted(self.hours.items()):
            following = next_bucket(timestamp, 'hour', self.zone)
            entry = {'start_utc': max(start, timestamp).isoformat(), 'end_utc': min(self.end, following).isoformat(),
                     'partial': timestamp < start or following > self.end, 'requests': hour.requests,
                     'installations': len(hour.candidate_ids), 'client_logins': len(hour.candidate_logins)}
            values = peaks.setdefault(bucket_start(timestamp, group or self.group, self.zone), {})
            for name, metric in (('busiest_hour', 'requests'), ('peak_installation_hour', 'installations'), ('peak_login_hour', 'client_logins')):
                if entry[metric] and (name not in values or entry[metric] > values[name][metric]):
                    values[name] = entry
        return peaks

    def serialise(self, first_seen: dict, earliest: dt.datetime | None, latest: dt.datetime | None, diagnostics: dict):
        start = self.start or earliest or self.end - dt.timedelta(days=1)
        cursor = bucket_start(start, self.group, self.zone)
        hour_peaks = self.hour_peaks(start)
        observed_end = min(self.end, dt.datetime.fromtimestamp(math.floor(latest.timestamp()) + 1, UTC)) if latest else start
        new = collections.Counter()
        new_days = collections.Counter()
        for sid in self.totals['candidate'].ids:
            stamp = first_seen[sid]
            if start <= stamp < self.end:
                new[bucket_start(stamp, self.group, self.zone)] += 1
                new_days[bucket_start(stamp, 'day', self.zone)] += 1
        rows = []
        while cursor < self.end:
            if len(rows) >= MAX_BUCKETS:
                raise ValueError('Too many output buckets; choose a coarser grouping')
            following = next_bucket(cursor, self.group, self.zone)
            counts = self.buckets.get(cursor, {category: Bucket() for category in ('all',) + CATEGORIES})
            observed = earliest is not None and latest is not None and following > earliest and cursor <= latest
            duration = max(0, (min(following, observed_end) - max(cursor, start, earliest or start)).total_seconds())
            requests = counts['all'].requests
            load = {'observed_seconds': duration, 'average_requests_per_second': requests / duration if duration else None,
                    'average_requests_per_hour': requests * 3600 / duration if duration else None,
                    'peak_second': self.second_peaks.get(cursor), **hour_peaks.get(cursor, {})}
            rows.append({'start_utc': cursor.isoformat(), 'end_utc': following.isoformat(),
                         'label': cursor.astimezone(self.zone).isoformat(),
                         'partial': cursor < start or following > self.end,
                         'observed_history': observed,
                         'first_seen_installations': new[cursor],
                         'load': load,
                         'traffic': {name: value.serialise() for name, value in counts.items()}})
            cursor = following
        totals = {name: value.serialise() for name, value in self.totals.items()}
        if sum(row['traffic']['all']['requests'] for row in rows) != totals['all']['requests'] or sum(row['traffic']['all']['bytes'] for row in rows) != totals['all']['bytes']:
            raise ValueError('Grouped request/data totals do not reconcile; reports retained')
        counts = sorted(self.candidate_counts.values(), reverse=True)
        load_days = []
        if self.load_enabled:
            days = self.hour_peaks(start, 'day')
            day = bucket_start(start, 'day', self.zone)
            while day < self.end:
                if len(load_days) >= MAX_LOAD_DAYS:
                    raise ValueError('Too many rate-summary days; choose a shorter date range')
                following = next_bucket(day, 'day', self.zone)
                duration = max(0, (min(following, observed_end) - max(day, start, earliest or start)).total_seconds())
                load_days.append({'date': day.astimezone(self.zone).date().isoformat(),
                                  'start_utc': day.isoformat(), 'end_utc': following.isoformat(),
                                  'observed_seconds': duration, 'first_seen_installations': new_days[day],
                                  **self.day_totals.get(day, {'requests': 0, 'bytes': 0}),
                                  'peak_second': self.day_second_peaks.get(day), **days.get(day, {})})
                day = following
            if sum(item['requests'] for item in load_days) != totals['all']['requests']:
                raise ValueError('Daily request-rate counts do not reconcile; reports retained')
        return {'schema_version': 1, 'name': self.name, 'title': self.title,
                'period': {'from': start.isoformat(), 'to_exclusive': self.end.isoformat(), 'timezone': str(self.zone), 'group': self.group},
                'source': {'earliest': earliest.isoformat() if earliest else None, 'data_through': latest.isoformat() if latest else None, **dict(diagnostics)},
                'totals': totals, 'rows': rows, 'load_days': load_days,
                'candidate_activity': {'first_seen_installations': sum(new.values()),
                                       'previously_seen_installations': len(self.totals['candidate'].ids) - sum(new.values()),
                                       'multiple_local_dates': sum(len(days) >= 2 for days in self.candidate_dates.values()),
                                       'at_least_seven_local_dates': sum(len(days) >= 7 for days in self.candidate_dates.values()),
                                       'top_10_request_share_pct': round(100 * sum(counts[:10]) / self.totals['candidate'].requests, 2) if self.totals['candidate'].requests else 0},
                'versions': [{'version': key, 'requests': value['requests'], 'installations': len(value['ids']), 'builds': sorted(value['builds'])}
                             for key, value in sorted(self.versions.items(), key=lambda pair: -pair[1]['requests'])],
                'definitions': {'installation': 'One application database, not a verified person; resets can create new IDs.',
                                'candidate': 'Map requests with a standard installation ID, excluding reported demos and configured internal IDs. Unknown internal traffic may remain.',
                                'first_seen': 'First successful map request in retained history and the private first-seen ledger, not registration time.',
                                'login': 'Distinct installation/login-reference pairs observed fetching maps, not all logins or people.',
                                'latency': 'Server-side duration estimates from fixed millisecond histogram buckets; not browser latency.',
                                'request_rates': 'All map requests, including failures, demos and tests. Average rates include idle time within retained observation timestamps. Partial edge buckets are clipped.',
                                'peak_second': 'Exact count in a fixed logged second, not a rolling second, arrival rate or concurrent requests. Ties show the earliest timestamp.',
                                'hourly_activity': 'Distinct candidate installations and installation/login-reference pairs with successful map requests in a local clock hour, not simultaneous users. Repeated DST hours are separate.',
                                'instance_type': 'Reported demo/standard mode, not ownership. Missing historical values remain unknown.',
                                'browser_environment': 'Browser/device snapshots remain in application databases and are not supplied by map logs.'}}


def analyse(log_dir: Path, scopes: list[Scope], classifications: dict, pattern: str, saved_first_seen: dict | None = None, work_dir: Path | None = None):
    if len(scopes) > 32:
        raise ValueError('At most 32 scopes can be processed in one scan')
    if work_dir is not None:
        work_dir.mkdir(parents=True, exist_ok=True, mode=0o700)
    with tempfile.TemporaryDirectory(prefix='rates-', dir=work_dir) as temporary:
        return analyse_stream(log_dir, scopes, classifications, pattern, saved_first_seen, SecondCounts(Path(temporary)))


def analyse_stream(log_dir, scopes, classifications, pattern, saved_first_seen, seconds):
    scan_started = time.monotonic()
    diagnostics = collections.Counter({name: 0 for name in ('files', 'compressed_files', 'plain_files', 'stored_bytes', 'read_bytes',
        'lines', 'parsed_records', 'nginx_records', 'caddy_records', 'map_requests', 'health_requests', 'other_requests',
        'parse_errors', 'records_after_cutoff', 'incomplete_tail_records')})
    first_seen = dict(saved_first_seen or {})
    earliest = latest = None
    cutoff = max(scope.end for scope in scopes)
    for record in read_logs(log_dir, pattern, diagnostics):
        if record.timestamp >= cutoff:
            diagnostics['records_after_cutoff'] += 1
            continue
        earliest = min(earliest, record.timestamp) if earliest else record.timestamp
        latest = max(latest, record.timestamp) if latest else record.timestamp
        if not record.path.lower().endswith('.pmtiles'):
            diagnostics['health_requests' if record.path == '/health' else 'other_requests'] += 1
            continue
        diagnostics['map_requests'] += 1
        if STANDARD_ID.fullmatch(record.server_id) and 200 <= record.status < 400:
            if record.server_id not in first_seen and len(first_seen) >= MAX_IDENTITIES:
                raise ValueError('Installation limit exceeded; reports retained')
            first_seen[record.server_id] = min(first_seen.get(record.server_id, record.timestamp), record.timestamp)
        category = category_for(record, classifications)
        mask = 0
        for index, scope in enumerate(scopes):
            if scope.add(record, category) and scope.load_enabled:
                mask |= 1 << index
        seconds.add(record.timestamp, mask)
    if diagnostics['parse_errors']:
        raise ValueError(f'{diagnostics["parse_errors"]} access-log records could not be parsed; reports retained')
    diagnostics['scan_seconds'] = time.monotonic() - scan_started
    diagnostics['evaluated_scopes'] = len(scopes)
    LOGGER.info('Scan completed: files=%d records=%d map_requests=%d health=%d incomplete_tails=%d', diagnostics['files'], diagnostics['parsed_records'], diagnostics['map_requests'], diagnostics['health_requests'], diagnostics['incomplete_tail_records'])
    summarize_started = time.monotonic()
    seconds.finish(scopes)
    reports = [scope.serialise(first_seen, earliest, latest, diagnostics) for scope in scopes]
    summarize_seconds = time.monotonic() - summarize_started
    for item in reports:
        item['source']['summarize_seconds'] = summarize_seconds
    if (log_dir / '.mtl-map-reports-demo').is_file():
        for item in reports:
            item['demo_dataset'] = True
            item['definitions']['data_source'] = 'Synthetic demonstration logs. Example metrics do not describe production traffic.'
    return reports, first_seen


def publish(root: Path, reports: list[dict], assets: Path, job_started: float | None = None, saved_processing: dict | None = None) -> None:
    render_started = time.monotonic()
    if not all((assets / name).is_file() for name in ('styles.css', 'echarts.min.js', 'icons.svg')):
        raise FileNotFoundError('Built report assets missing; run npm ci and npm run build in analytics/')
    template = (ROOT / 'templates/report.html').read_text()
    application = (ROOT / 'templates/app.js').read_text()
    theme = (ROOT / 'templates/theme.js').read_text()
    generated_at = dt.datetime.now(UTC)
    run_id = generated_at.strftime('%Y%m%dT%H%M%SZ') + '-' + uuid.uuid4().hex[:8]
    work = root / 'private' / 'work'
    work.mkdir(parents=True, exist_ok=True, mode=0o700)
    work.chmod(0o700)
    stage = work / run_id
    generations = root / 'public' / 'generations'
    current = root / 'public' / 'current'
    # The common job lock makes any remaining work directory an abandoned run.
    for abandoned in work.iterdir():
        if abandoned.is_dir() and not abandoned.is_symlink():
            shutil.rmtree(abandoned)
    stage.mkdir(parents=True, mode=0o755)
    generations.mkdir(parents=True, exist_ok=True)
    try:
        shutil.copytree(assets, stage / 'assets')
        links = [{'name': report['name'], 'title': report['title']} for report in reports]
        for report in reports:
            report['generated_at'] = generated_at.isoformat()
            report['scopes'] = links
            payload = json.dumps(report, separators=(',', ':'), allow_nan=False).replace('<', '\\u003c')
            text = template.replace('__TITLE__', html.escape(report['title'])).replace('__THEME__', theme).replace('__APPLICATION__', application).replace('__REPORT__', payload)
            (stage / (report['name'] + '.html')).write_text(text)
            atomic_json(stage / (report['name'] + '.json'), report, 0o644)
        default = next((item for item in reports if item['name'] == 'last-three-months-daily'), reports[0])
        shutil.copyfile(stage / (default['name'] + '.html'), stage / 'index.html')
        for path in stage.rglob('*'):
            path.chmod(0o755 if path.is_dir() else 0o644)
        stage.chmod(0o755)
        ready_at = time.monotonic()
        processing = dict(saved_processing or {})
        processing.update({'generated_at': generated_at.isoformat(), 'render_seconds': ready_at - render_started,
                           'reports_generated': len(reports), 'output_buckets': sum(len(item['rows']) for item in reports),
                           'cached_render': job_started is None})
        if job_started is not None:
            usage = resource.getrusage(resource.RUSAGE_SELF)
            processing.update({'total_seconds': ready_at - job_started,
                               'peak_process_rss_bytes': usage.ru_maxrss * (1 if sys.platform == 'darwin' else 1024),
                               'timing_boundary': 'ready_to_publish'})
        atomic_json(stage / 'assets/processing.json', processing, 0o644)
        destination = generations / run_id
        os.replace(stage, destination)
        temporary = root / 'public' / ('.current-' + uuid.uuid4().hex)
        temporary.symlink_to(Path('generations') / run_id, target_is_directory=True)
        os.replace(temporary, root / 'public' / 'current')
        LOGGER.info('Published reports: scopes=%d generation=%s', len(reports), run_id)
        # A successful refresh replaces the report set, without retaining history.
        keep = {destination.name}
        stale = [path for path in generations.iterdir() if path.is_dir() and path.name not in keep]
        for path in stale:
            try:
                shutil.rmtree(path)
            except OSError as error:
                LOGGER.warning('Old generation cleanup failed: generation=%s error=%s message=%s', path.name, type(error).__name__, error)
        completed_at = time.monotonic()
        processing['render_seconds'] = completed_at - render_started
        if job_started is not None:
            processing['total_seconds'] = completed_at - job_started
            processing['timing_boundary'] = 'published'
        try:
            atomic_json(destination / 'assets/processing.json', processing, 0o644)
        except OSError as error:
            LOGGER.warning('Final processing timing update failed: error=%s message=%s; ready-to-publish metrics retained', type(error).__name__, error)
    finally:
        if stage.exists():
            shutil.rmtree(stage)


def make_scopes(args, zone: ZoneInfo, now: dt.datetime):
    end = parse_date(args.to_date, zone) if args.to_date else now
    if args.from_date or args.days is not None or args.group is not None:
        start = parse_date(args.from_date, zone) if args.from_date else recent_start(end, args.days, zone) if args.days is not None else None
        if start is not None and start >= end:
            raise ValueError('From must be earlier than the exclusive to date')
        group = args.group or 'day'
        return [Scope('custom', 'Custom period · ' + group, start, end, group, zone)]
    scopes = []
    for name, title, days, group in PRESETS:
        start = recent_start(end, days, zone)
        if name in ('last-month-hourly', 'last-three-months-daily'):
            start = recent_month_start(end, 1 if name == 'last-month-hourly' else 3, zone)
        scopes.append(Scope(name, title, start, end, group, zone))
    scopes.append(Scope('all-history-weekly', 'All retained history · weekly', None, end, 'week', zone))
    return scopes


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--log-dir', type=Path, default=DEFAULT_LOGS)
    parser.add_argument('--pattern', default='access.log*', help='For Caddy, select only the map host access logs.')
    parser.add_argument('--output-root', type=Path, default=DEFAULT_OUTPUT, help='Dedicated folder for private job files and public reports.')
    parser.add_argument('--from', dest='from_date', help='Inclusive ISO date/time; date-only uses local midnight.')
    parser.add_argument('--to', dest='to_date', help='Exclusive ISO date/time; default now.')
    parser.add_argument('--days', type=int, help='Custom range ending at --to or now, in local calendar days.')
    parser.add_argument('--group', choices=GROUPS, help='Without --from/--days, group all retained history.')
    parser.add_argument('--timezone', default='Europe/Zurich')
    parser.add_argument('--classifications', type=Path)
    parser.add_argument('--known-id-root', action='append', type=Path, default=[])
    parser.add_argument('--lock-file', type=Path, help='Defaults to output-root/private/report.lock; use the same lock for all jobs on this dataset.')
    parser.add_argument('--assets-dir', type=Path, default=ROOT / 'assets')
    parser.add_argument('--settings', type=Path, help='Container-generated job settings.')
    parser.add_argument('--log-level', choices=('INFO', 'DEBUG'), default='INFO')
    args = parser.parse_args(argv)
    if args.settings:
        args.output_root = args.settings.resolve().parent.parent
    args.output_root = args.output_root.resolve()
    configure_logging(args.output_root, args.log_level)
    started = time.monotonic()
    try:
        if args.settings:
            settings = json.loads(args.settings.read_text())
            if not settings.get('enabled'):
                return 0
            if Path(settings['output_root']).resolve() != args.output_root:
                raise ValueError('Job settings must use their dedicated analytics folder')
            args.log_dir = Path(settings['log_dir'])
            args.timezone = settings['timezone']
            args.classifications = Path(settings['classifications'])
        with run_lock(args.lock_file or args.output_root / 'private/report.lock') as acquired:
            if not acquired:
                return 0
            LOGGER.info('Report job started: grouping=%s timezone=%s', args.group or 'preset scopes', args.timezone)
            if args.days is not None and args.days <= 0:
                raise ValueError('Days must be positive')
            if args.days is not None and args.from_date:
                raise ValueError('Choose --from or --days, not both')
            if not args.log_dir.is_dir():
                raise FileNotFoundError(f'Access-log directory does not exist: {args.log_dir}')
            zone = ZoneInfo(args.timezone)
            scopes = make_scopes(args, zone, dt.datetime.now(UTC))
            comparisons = {scope.name: Scope(scope.name + '-previous', 'Previous equivalent period', scope.start - (scope.end - scope.start), scope.start, scope.group, zone, load_enabled=False)
                           for scope in scopes if scope.start is not None}
            registry = load_classifications(args.classifications, args.known_id_root)
            LOGGER.info('Prepared scopes=%d configured_installations=%d', len(scopes), len(registry))
            ledger = args.output_root / 'private/first-seen.json'
            saved = {key: parse_date(value, zone) for key, value in json.loads(ledger.read_text()).items()} if ledger.exists() else {}
            combined, first_seen = analyse(args.log_dir, scopes + list(comparisons.values()), registry, args.pattern, saved, args.output_root / 'private/work')
            reports = combined[:len(scopes)]
            previous = {item['name']: item for item in combined[len(scopes):]}
            for item in reports:
                if item['name'] in comparisons:
                    old = previous[item['name'] + '-previous']
                    earliest = parse_date(old['source']['earliest'], zone) if old['source']['earliest'] else None
                    latest = parse_date(old['source']['data_through'], zone) if old['source']['data_through'] else None
                    period = comparisons[item['name']]
                    item['comparison'] = {'period': old['period'], 'totals': old['totals'], 'candidate_activity': old['candidate_activity'],
                                          'history_covers_period': earliest is not None and latest is not None and earliest <= period.start and latest >= period.end}
            for report in reports:
                total = report['totals']['all']
                LOGGER.info('Scope completed: name=%s buckets=%d requests=%d bytes=%d candidate_installations=%d', report['name'], len(report['rows']), total['requests'], total['bytes'], report['totals']['candidate']['installations'])
            atomic_json(ledger, {key: value.isoformat() for key, value in first_seen.items()})
            publish(args.output_root, reports, args.assets_dir, job_started=started)
            LOGGER.info('Report job completed: duration_seconds=%.2f', time.monotonic() - started)
        return 0
    except Exception as error:
        LOGGER.exception('Report job failed: type=%s duration_seconds=%.2f; previous reports retained', type(error).__name__, time.monotonic() - started)
        return 1


if __name__ == '__main__':
    sys.exit(main())
