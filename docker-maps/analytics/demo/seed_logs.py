#!/usr/bin/env python3
"""Create owned, synthetic historical map logs for the static-report demo."""
import argparse
import collections
import datetime as dt
import gzip
import json
import logging
import math
import os
from pathlib import Path
import sys
from zoneinfo import ZoneInfo

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import report

ALPHABET = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz'
MARKER = '.mtl-map-reports-demo'


def identifier(number, length=22):
    text = ''
    while number:
        number, remainder = divmod(number, len(ALPHABET))
        text = ALPHABET[remainder] + text
    return text.rjust(length, '1')


def claim_folder(path):
    marker = path / MARKER
    if path.exists() and any(path.iterdir()) and not marker.is_file():
        raise ValueError(f'Demo refuses to overwrite an unowned folder: {path}')
    path.mkdir(parents=True, exist_ok=True)
    marker.write_text('Owned synthetic demo data. Never use this folder for production logs.\n')


def samples_from_real_logs(path, pattern):
    samples = []
    stream = report.read_logs(path, pattern, collections.Counter())
    try:
        for record in stream:
            if record.path.lower().endswith('.pmtiles'):
                samples.append((record.size, record.duration_ms, record.status))
                if len(samples) == 512:
                    break
    finally:
        stream.close()
    if not samples:
        raise ValueError('No map traffic available for real-log-derived samples')
    report.LOGGER.info('Sampled real map-response distributions: samples=%d; original identities and timestamps are discarded', len(samples))
    return samples


def seed(logs, data, anchor, days=730, zone=ZoneInfo('Europe/Zurich'), source=None, pattern='access.log*'):
    claim_folder(logs)
    claim_folder(data)
    report.configure_logging(data)
    report.LOGGER.info('Historical demo seeding started: calendar_days=%d anchor=%s', days, anchor.isoformat())
    samples = samples_from_real_logs(source, pattern) if source else None
    for path in logs.glob('access.log*'):
        if path.is_file():
            path.unlink()
    private = data / 'private'
    (private / 'first-seen.json').unlink(missing_ok=True)
    local = anchor.astimezone(zone)
    args = argparse.Namespace(from_date=None, to_date=anchor.isoformat(), days=None, group=None)
    history_start = dt.datetime.combine(local.date() - dt.timedelta(days=days), dt.time(), zone).astimezone(report.UTC)
    bounds = {scope.name: scope.start or history_start for scope in report.make_scopes(args, zone, anchor)}
    expected = {name: {'requests': 0, 'bytes': 0, 'categories': {key: {'requests': 0, 'bytes': 0} for key in report.CATEGORIES}, 'candidate_ids': set()} for name in bounds}
    classifications = {identifier(number): {'category': 'internal'} for number in range(1001,1005)}
    classifications.update({identifier(number): {'category': 'demo'} for number in range(2001,2009)})
    records = compressed_files = 0
    for index in range(days + 1):
        date = local.date() - dt.timedelta(days=days-index)
        age = (local.date() - date).days
        compressed = age > 3
        filename = 'access.log' if age == 0 else 'access.log-' + date.strftime('%Y%m%d') + ('.gz' if compressed else '')
        path = logs / filename
        opener = gzip.open if compressed else open
        with opener(path,'wt',encoding='utf-8') as handle:
            health = dt.datetime.combine(date,dt.time(),zone).astimezone(report.UTC)
            if health < anchor:
                handle.write(f'{health.isoformat()} remote_addr=192.0.2.1 request="GET /health HTTP/1.1" status=200 bytes=2 mtl_version="" mtl_server_id="" ua="demo" referer="" request_time=0.000\n')
            count = 30 + index // 8 + int(18*(1+math.sin(index/8)))
            for event in range(count):
                stamp = dt.datetime.combine(date,dt.time((event*7+index%3)%24,17,event%60),zone).astimezone(report.UTC)
                if stamp >= anchor:
                    continue
                category = 'internal' if event%10==0 else 'demo' if event%10 in (1,2) else 'candidate'
                number = 1001+event%4 if category=='internal' else 2001+event%8 if category=='demo' else 10+(event+index*7)%(8+index//20)
                sid = identifier(number)
                if samples:
                    size,duration,status = samples[(index+event)%len(samples)]
                else:
                    size = 8192 + (event*7919+index*101)%180000
                    duration = (1,2,4,8,16,32)[event%6]
                    status = 503 if event%61==60 else 206
                modern = age <= 90
                instance = 'demo' if category=='demo' else 'standard'
                login = identifier(index*1000+number,10) if modern and event%3 else ''
                version = '1.600-demo' if modern else '1.510-demo' if age<=365 else '1.400-demo'
                extra = f'mtl_build="{date.isoformat()}T10_00_00Z" mtl_instance_type="{instance}" mtl_client_login_ref="{login}" forwarded_for="192.0.2.10" real_ip="192.0.2.10" ' if modern else ''
                limit = max(0,size-1)
                content_range = f'bytes 0-{limit}/999999999' if status==206 else ''
                handle.write(f'{stamp.isoformat()} remote_addr=192.0.2.1 request="GET /prod/planet.pmtiles HTTP/1.1" status={status} bytes={size} request_range="bytes=0-{limit}" response_content_range="{content_range}" mtl_version="{version}" mtl_server_id="{sid}" {extra}ua="synthetic-demo" referer="" request_time={duration/1000:.3f}\n')
                records += 1
                for name,start in bounds.items():
                    if start <= stamp < anchor:
                        item=expected[name];item['requests']+=1;item['bytes']+=size
                        item['categories'][category]['requests']+=1;item['categories'][category]['bytes']+=size
                        if category=='candidate' and 200<=status<400:
                            item['candidate_ids'].add(sid)
        compressed_files += compressed
        modified = dt.datetime.combine(date,dt.time(23,59),zone).timestamp()
        os.utime(path,(modified,modified))
    for item in expected.values():
        item['candidate_installations']=len(item.pop('candidate_ids'))
    report.atomic_json(private / 'classifications.json',{'installations':classifications})
    report.atomic_json(private / 'demo-expected.json',{'anchor':anchor.isoformat(),'calendar_days':days,'records':records,'compressed_files':compressed_files,'scopes':expected})
    report.LOGGER.info('Historical demo seeding completed: map_records=%d files=%d gzip_files=%d', records, days+1, compressed_files)
    return expected


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--log-dir',type=Path,default=Path('/data/logs'))
    parser.add_argument('--data-dir',type=Path,default=Path('/app/analytics/data'))
    parser.add_argument('--anchor',help='Fixed ISO timestamp for verification; default now.')
    parser.add_argument('--days',type=int,default=730)
    parser.add_argument('--timezone',default=os.environ.get('MAP_REPORTS_TIMEZONE','Europe/Zurich'))
    parser.add_argument('--source-log-dir',type=Path,help='Privately sample real response sizes/statuses/durations; no real identities are copied.')
    parser.add_argument('--source-pattern',default='access.log*')
    args=parser.parse_args()
    if not 1<=args.days<=1460:
        parser.error('Days must be between 1 and 1460')
    logging.basicConfig(level=logging.INFO,format='%(asctime)s %(levelname)s %(message)s')
    try:
        zone=ZoneInfo(args.timezone)
        anchor=report.parse_date(args.anchor,zone) if args.anchor else dt.datetime.now(report.UTC)
        seed(args.log_dir,args.data_dir,anchor,args.days,zone,args.source_log_dir,args.source_pattern)
        return 0
    except Exception as error:
        report.LOGGER.exception('Historical demo seeding failed: type=%s',type(error).__name__)
        return 1


if __name__=='__main__':
    sys.exit(main())
