import collections
import datetime as dt
import gzip
import json
import os
from pathlib import Path
import sys
import tempfile
import types
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import report
import runtime

SID = '123456789ABCDEFGHJKLMN'
DEMO = '223456789ABCDEFGHJKLMN'
INTERNAL = '323456789ABCDEFGHJKLMN'


def line(stamp='2026-10-01T10:00:00+00:00', sid=SID, query='', fields='', status=206, size=100):
    return (f'{stamp} remote_addr=172.18.0.1 request="GET /prod/planet.pmtiles{query} HTTP/1.1" '
            f'status={status} bytes={size} mtl_version="1.524" mtl_server_id="{sid}" '
            f'{fields}ua="okhttp" referer="-" request_time=0.002\n')


class ReportTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.logs = self.root / 'logs'
        self.logs.mkdir()
        self.output = self.root / 'analytics'
        self.assets = self.root / 'assets'
        self.assets.mkdir()
        for name in ('styles.css', 'echarts.min.js', 'icons.svg'):
            (self.assets / name).write_text('/* synthetic test asset */')
        report.configure_logging(self.output)

    def tearDown(self):
        for handler in report.LOGGER.handlers:
            handler.close()
        report.LOGGER.handlers.clear()
        self.temp.cleanup()

    def scope(self, start='2026-10-01', end='2026-10-03', group='day', zone='Europe/Zurich'):
        zone = report.ZoneInfo(zone)
        return report.Scope('fixture', 'Fixture', report.parse_date(start, zone), report.parse_date(end, zone), group, zone)

    def analyse(self, text, scope=None, known=None, first=None):
        (self.logs / 'access.log').write_text(text)
        return report.analyse(self.logs, [scope or self.scope()], known or {}, 'access.log*', first)[0][0]

    def cli(self, extra=()):
        return report.main(['--log-dir', str(self.logs), '--output-root', str(self.output), '--assets-dir', str(self.assets), '--from', '2026-10-01', '--to', '2026-10-03', *extra])

    def test_identical_range_requests_remain_separate(self):
        r = self.analyse(line() * 3)
        self.assertEqual(r['totals']['all']['requests'], 3)
        self.assertEqual(r['totals']['candidate']['installations'], 1)
        self.assertEqual(r['totals']['all']['bytes'], 300)

    def test_input_diagnostics_count_stored_and_decoded_sizes_and_skipped_records(self):
        first=line()+line().replace('/prod/planet.pmtiles','/health')+line().replace('/prod/planet.pmtiles','/status')
        second=line('2026-10-04T10:00:00Z')
        (self.logs/'access.log').write_text(first+'partial tail')
        archive=self.logs/'access.log-old.gz'
        with gzip.open(archive,'wt') as handle:handle.write(second)
        value=report.analyse(self.logs,[self.scope()],{},'access.log*')[0][0]['source']
        self.assertEqual(value['files'],2);self.assertEqual(value['compressed_files'],1);self.assertEqual(value['plain_files'],1)
        self.assertEqual(value['stored_bytes'],(self.logs/'access.log').stat().st_size+archive.stat().st_size)
        self.assertEqual(value['read_bytes'],len((first+'partial tail'+second).encode()))
        self.assertEqual(value['parsed_records'],4);self.assertEqual(value['nginx_records'],4);self.assertEqual(value['caddy_records'],0)
        self.assertEqual(value['map_requests'],1);self.assertEqual(value['health_requests'],1);self.assertEqual(value['other_requests'],1)
        self.assertEqual(value['records_after_cutoff'],1);self.assertEqual(value['incomplete_tail_records'],1)
        self.assertGreaterEqual(value['scan_seconds'],0);self.assertGreaterEqual(value['summarize_seconds'],0)

    def test_processing_metadata_is_ready_with_the_report_generation(self):
        (self.logs/'access.log').write_text(line())
        self.assertEqual(self.cli(),0)
        current=self.output/'public/current'
        data=json.loads((current/'custom.json').read_text())
        processing=json.loads((current/'assets/processing.json').read_text())
        self.assertEqual(processing['generated_at'],data['generated_at'])
        self.assertFalse(processing['cached_render'])
        self.assertGreaterEqual(processing['total_seconds'],data['source']['scan_seconds']+data['source']['summarize_seconds'])
        self.assertGreaterEqual(processing['render_seconds'],0);self.assertGreater(processing['peak_process_rss_bytes'],0)
        self.assertEqual(processing['reports_generated'],1);self.assertEqual(processing['output_buckets'],len(data['rows']))

    def test_exact_second_peaks_merge_unordered_files_and_many_bounded_runs(self):
        (self.logs / 'access.log').write_text(line('2026-10-01T11:00:00Z') * 3 + line('2026-10-01T10:00:00Z') * 4)
        with gzip.open(self.logs / 'access.log-old.gz', 'wt') as handle:
            handle.write(line('2026-10-01T12:00:00Z') * 5 + line('2026-10-01T11:00:00Z') * 2)
        scope = self.scope('2026-10-01', '2026-10-02', zone='UTC')
        work = self.root / 'private-work'
        with mock.patch.object(report, 'RATE_CHUNK_KEYS', 1), mock.patch.object(report, 'RATE_MERGE_FILES', 2):
            r = report.analyse(self.logs, [scope], {}, 'access.log*', work_dir=work)[0][0]
        self.assertEqual(r['rows'][0]['load']['peak_second'], {'at_utc': '2026-10-01T11:00:00+00:00', 'requests': 5})
        self.assertEqual(r['load_days'][0]['peak_second']['requests'], 5)
        self.assertEqual(list(work.iterdir()), [])

    def test_rates_include_idle_time_and_hour_peaks_only_use_requested_scope(self):
        health = lambda stamp: line(stamp).replace('/prod/planet.pmtiles', '/health')
        records = health('2026-10-01T09:00:00Z') + health('2026-10-01T12:59:59Z')
        records += line('2026-10-01T09:01:00Z') * 20 + line('2026-10-01T10:00:00Z')
        records += line('2026-10-01T11:20:30Z') * 5
        r = self.analyse(records, self.scope('2026-10-01T10:00Z', '2026-10-01T13:00Z', zone='UTC'))
        load = r['rows'][0]['load']
        self.assertEqual(load['observed_seconds'], 10800)
        self.assertAlmostEqual(load['average_requests_per_second'], 6 / 10800)
        self.assertEqual(load['average_requests_per_hour'], 2)
        self.assertEqual(load['peak_second']['requests'], 5)
        self.assertEqual(load['busiest_hour']['start_utc'], '2026-10-01T11:00:00+00:00')
        self.assertEqual(load['busiest_hour']['requests'], 5)

    def test_hourly_activity_deduplicates_candidate_ids_and_logins(self):
        records = line(fields='mtl_client_login_ref="a" ') * 4
        records += line(fields='mtl_client_login_ref="b" ')
        records += line(sid=INTERNAL, fields='mtl_client_login_ref="c" ')
        records += line(sid=DEMO, fields='mtl_instance_type="demo" mtl_client_login_ref="d" ')
        records += line(sid='423456789ABCDEFGHJKLMN', status=503)
        r = self.analyse(records, self.scope(zone='UTC'), known={INTERNAL: 'internal'})
        day = r['load_days'][0]
        self.assertEqual(day['busiest_hour']['requests'], 8)
        self.assertEqual(day['peak_installation_hour']['installations'], 1)
        self.assertEqual(day['peak_login_hour']['client_logins'], 2)

    def test_partial_second_ranges_count_only_matching_records(self):
        records = line('2026-10-01T10:00:00.100Z') * 10 + line('2026-10-01T10:00:00.600Z') * 2
        scopes = [self.scope('2026-10-01T10:00:00.500Z', '2026-10-01T10:00:01Z', zone='UTC'),
                  self.scope('2026-10-01T10:00:00Z', '2026-10-01T10:00:01Z', zone='UTC')]
        (self.logs / 'access.log').write_text(records)
        results = report.analyse(self.logs, scopes, {}, 'access.log*')[0]
        self.assertEqual(results[0]['rows'][0]['load']['peak_second']['requests'], 2)
        self.assertEqual(results[1]['rows'][0]['load']['peak_second']['requests'], 12)
        self.assertEqual(results[0]['rows'][0]['load']['observed_seconds'], .5)
        self.assertTrue(results[0]['rows'][0]['load']['busiest_hour']['partial'])

    def test_repeated_dst_hours_remain_separate_and_day_has_25_hours(self):
        health = lambda stamp: line(stamp).replace('/prod/planet.pmtiles', '/health')
        records = health('2026-10-24T22:00:00Z') + health('2026-10-25T22:59:59Z')
        records += line('2026-10-25T00:15:00Z') * 4
        records += line('2026-10-25T01:15:00Z') * 3 + line('2026-10-25T01:30:00Z', sid=INTERNAL)
        scope = self.scope('2026-10-25', '2026-10-26')
        r = self.analyse(records, scope)
        day = r['load_days'][0]
        self.assertEqual(day['observed_seconds'], 90000)
        self.assertEqual(day['busiest_hour']['requests'], 4)
        self.assertEqual(day['busiest_hour']['start_utc'], '2026-10-25T00:00:00+00:00')
        self.assertEqual(day['peak_installation_hour']['start_utc'], '2026-10-25T01:00:00+00:00')
        self.assertEqual(day['peak_installation_hour']['installations'], 2)

    def test_rate_scratch_is_removed_when_parsing_fails(self):
        (self.logs / 'access.log').write_text(line() + 'invalid record\n')
        work = self.root / 'private-work'
        with mock.patch.object(report, 'RATE_CHUNK_KEYS', 1), self.assertRaises(ValueError):
            report.analyse(self.logs, [self.scope()], {}, 'access.log*', work_dir=work)
        self.assertEqual(list(work.iterdir()), [])

    def test_missing_history_has_no_rates_or_peak_activity(self):
        r = self.analyse('', self.scope())
        self.assertIsNone(r['rows'][0]['load']['average_requests_per_second'])
        self.assertIsNone(r['rows'][0]['load']['peak_second'])
        self.assertEqual(r['load_days'][0]['observed_seconds'], 0)
        self.assertNotIn('busiest_hour', r['load_days'][0])

    def test_demo_mode_and_known_internal_ids_partition_traffic(self):
        r = self.analyse(line() + line(sid=DEMO, fields='mtl_instance_type="demo" ') + line(sid=INTERNAL), known={INTERNAL: 'internal'})
        for kind in ('candidate', 'demo', 'internal'):
            self.assertEqual(r['totals'][kind]['requests'], 1)
        self.assertEqual(sum(r['totals'][kind]['bytes'] for kind in report.CATEGORIES), r['totals']['all']['bytes'])

    def test_old_requests_have_unknown_type_without_becoming_demos(self):
        r = self.analyse(line())
        self.assertEqual(r['totals']['candidate']['instance_types']['unknown'], 1)
        self.assertEqual(r['totals']['demo']['requests'], 0)

    def test_login_query_fallbacks_and_logged_field_precedence(self):
        cases = [
            (line(query='?mtl-client-login-ref=current'), 'current'),
            (line(query='?mtl-user-session-id=legacy'), 'legacy'),
            (line(query='?mtl-client-login-ref=ignored', fields='mtl_client_login_ref="logged" '), 'logged'),
            (line(fields='mtl_user_session_id="old" '), 'old'),
        ]
        for text, expected in cases:
            self.assertEqual(report.parse_record(text).login_ref, expected)

    def test_instance_type_query_fallback_and_reordered_fields(self):
        entry = report.parse_record(line(query='?mtl-instance-type=demo', fields='mtl_build="2026-10-01" request_range="bytes=0-99" response_content_range="bytes 0-99/1000" '))
        self.assertEqual(entry.instance_type, 'demo')
        self.assertTrue(entry.request_range and entry.response_range)
        self.assertEqual(entry.build, '2026-10-01')

    def test_caddy_json_uses_same_metrics_and_forwarding_coverage(self):
        stamp = dt.datetime(2026, 10, 1, 10, tzinfo=report.UTC).timestamp()
        text = json.dumps({'ts': stamp, 'status': 206, 'size': 100, 'duration': .002,
                           'request': {'uri': '/prod/planet.pmtiles?mtl-server-id=' + SID + '&mtl-instance-type=demo&mtl-client-login-ref=login', 'headers': {'X-Forwarded-For': ['192.0.2.10'], 'Range': ['bytes=0-99']}}, 'resp_headers': {'Content-Range': ['bytes 0-99/1000']}}) + '\n'
        r = self.analyse(text)
        self.assertEqual(r['totals']['demo']['requests'], 1)
        self.assertEqual(r['totals']['all']['coverage']['forwarded_ip'], 1)
        self.assertEqual(r['totals']['demo']['client_logins'], 1)

    def test_gzipped_rotations_and_current_logs_are_both_read(self):
        with gzip.open(self.logs / 'access.log-20261001.gz', 'wt') as handle:
            handle.write(line())
        r = self.analyse(line('2026-10-02T10:00:00+00:00'))
        self.assertEqual(r['totals']['all']['requests'], 2)

    def test_range_end_is_exclusive_and_history_precedes_selected_range(self):
        r = self.analyse(line('2026-09-01T10:00:00+00:00') + line() + line('2026-10-02T22:00:00+00:00'))
        self.assertEqual(r['totals']['candidate']['requests'], 1)
        self.assertEqual(r['candidate_activity']['first_seen_installations'], 0)
        self.assertEqual(r['candidate_activity']['previously_seen_installations'], 1)

    def test_saved_first_seen_survives_deleted_old_logs(self):
        first = {SID: dt.datetime(2026, 7, 1, tzinfo=report.UTC)}
        r = self.analyse(line(), first=first)
        self.assertEqual(r['candidate_activity']['first_seen_installations'], 0)

    def test_day_counts_are_not_added_for_unique_period_total(self):
        r = self.analyse(line() + line('2026-10-02T10:00:00+00:00'))
        self.assertEqual(sum(row['traffic']['candidate']['installations'] for row in r['rows']), 2)
        self.assertEqual(r['totals']['candidate']['installations'], 1)
        self.assertEqual(r['candidate_activity']['multiple_local_dates'], 1)

    def test_hour_grouping_keeps_both_autumn_clock_change_hours(self):
        scope = self.scope('2026-10-25', '2026-10-26', 'hour')
        r = self.analyse(line('2026-10-25T00:30:00+00:00') + line('2026-10-25T01:30:00+00:00'), scope)
        active = [row for row in r['rows'] if row['traffic']['all']['requests']]
        self.assertEqual(len(r['rows']), 25)
        self.assertEqual(len(active), 2)
        self.assertNotEqual(active[0]['label'], active[1]['label'])

    def test_hour_grouping_handles_partial_hour_clock_changes(self):
        scope = self.scope('2026-10-04', '2026-10-05', 'hour', 'Australia/Lord_Howe')
        r = self.analyse(line('2026-10-03T15:45:00+00:00') + line('2026-10-03T16:15:00+00:00'), scope)
        self.assertEqual(sum(row['traffic']['all']['requests'] for row in r['rows']), 2)
        scope = self.scope('2026-04-05', '2026-04-06', 'hour', 'Australia/Lord_Howe')
        r = self.analyse(line('2026-04-04T14:15:00+00:00') + line('2026-04-04T15:15:00+00:00'), scope)
        row = next(row for row in r['rows'] if row['traffic']['all']['requests'])
        self.assertGreater(report.parse_date(row['end_utc'], report.ZoneInfo('UTC')), report.parse_date('2026-04-04T15:15:00Z', report.ZoneInfo('UTC')))

    def test_week_grouping_starts_monday_and_marks_partial_edges(self):
        scope = self.scope('2026-10-01', '2026-10-10', 'week')
        r = self.analyse(line(), scope)
        self.assertEqual(r['rows'][0]['label'][:10], '2026-09-28')
        self.assertTrue(r['rows'][0]['partial'] and r['rows'][-1]['partial'])

    def test_three_month_preset_uses_calendar_months(self):
        args = types.SimpleNamespace(to_date='2026-10-31T12:00:00+01:00', from_date=None, days=None, group=None)
        scopes = report.make_scopes(args, report.ZoneInfo('Europe/Zurich'), dt.datetime.now(report.UTC))
        start = next(s for s in scopes if s.name == 'last-three-months-daily').start.astimezone(report.ZoneInfo('Europe/Zurich'))
        self.assertEqual(start.date().isoformat(), '2026-07-31')

    def test_presets_are_ordered_by_timeframe_then_grouping(self):
        zone = report.ZoneInfo('Europe/Zurich')
        args = types.SimpleNamespace(to_date='2026-10-07T14:00:00+02:00', from_date=None, days=None, group=None)
        scopes = report.make_scopes(args, zone, dt.datetime.now(report.UTC))
        self.assertEqual([scope.name for scope in scopes], [
            'last-week-hourly', 'last-week-daily', 'last-month-hourly',
            'last-three-months-daily', 'last-year-daily', 'last-year-weekly', 'all-history-weekly'])
        yearly = [scope for scope in scopes if scope.name.startswith('last-year-')]
        self.assertEqual([scope.group for scope in yearly], ['day', 'week'])
        self.assertEqual(yearly[0].start, yearly[1].start)
        self.assertEqual(yearly[0].end, yearly[1].end)
        self.assertEqual((yearly[0].end.astimezone(zone) - yearly[0].start.astimezone(zone)).days, 365)

    def test_month_hourly_preset_clamps_month_end_and_retains_local_time_across_dst(self):
        zone = report.ZoneInfo('Europe/Zurich')
        for year, february_day in [(2024, 29), (2026, 28)]:
            args = types.SimpleNamespace(to_date=f'{year}-03-31T14:30:00+02:00', from_date=None, days=None, group=None)
            scopes = report.make_scopes(args, zone, dt.datetime.now(report.UTC))
            month = next(scope for scope in scopes if scope.name == 'last-month-hourly')
            self.assertEqual(month.group, 'hour')
            self.assertEqual(month.start.isoformat(), f'{year}-02-{february_day}T13:30:00+00:00')
            self.assertEqual(month.end.isoformat(), f'{year}-03-31T12:30:00+00:00')

    def test_missing_history_is_distinct_from_zero_traffic(self):
        scope = self.scope('2026-09-29', '2026-10-03')
        r = self.analyse(line(), scope)
        self.assertFalse(r['rows'][0]['observed_history'])
        self.assertTrue(r['rows'][2]['observed_history'])

    def test_short_and_legacy_server_ids_count_as_installations(self):
        r = self.analyse(line() + line(sid=SID[:10]) + line(sid=SID[:11]))
        self.assertEqual(r['totals']['candidate']['installations'], 2)

    def test_health_checks_do_not_enter_map_metrics(self):
        r = self.analyse(line() + line().replace('/prod/planet.pmtiles', '/health'))
        self.assertEqual(r['totals']['all']['requests'], 1)
        self.assertEqual(r['source']['health_requests'], 1)

    def test_failed_map_requests_count_as_traffic_without_active_installations(self):
        r = self.analyse(line(status=503))
        self.assertEqual(r['totals']['candidate']['requests'], 1)
        self.assertEqual(r['totals']['candidate']['installations'], 0)

    def test_run_skips_when_lock_is_held_and_logs_reason(self):
        with report.run_lock(self.output / 'private/report.lock') as held:
            self.assertTrue(held)
            self.assertEqual(self.cli(), 0)
        self.assertIn('Report run skipped', (self.output / 'private/report.log').read_text())
        self.assertFalse((self.output / 'public/current').exists())

    def test_failure_retains_previous_publication_and_logs_exception(self):
        (self.logs / 'access.log').write_text(line())
        self.assertEqual(self.cli(), 0)
        old = (self.output / 'public/current').resolve()
        (self.logs / 'access.log').write_text('malformed record\n')
        self.assertEqual(self.cli(), 1)
        self.assertEqual((self.output / 'public/current').resolve(), old)
        self.assertIn('ValueError', (self.output / 'private/report.log').read_text())

    def test_publication_does_not_export_raw_identifiers(self):
        (self.logs / 'access.log').write_text(line(fields='mtl_client_login_ref="private-login" '))
        self.assertEqual(self.cli(), 0)
        public = self.output / 'public/current'
        for name in ('custom.json', 'custom.html'):
            text = (public / name).read_text()
            self.assertNotIn(SID, text)
            self.assertNotIn('private-login', text)
        self.assertIn(SID, (self.output / 'private/first-seen.json').read_text())

    def test_successful_refreshes_replace_reports_without_accumulating(self):
        (self.logs / 'access.log').write_text(line())
        for _ in range(3):
            self.assertEqual(self.cli(), 0)
        self.assertEqual(len(list((self.output / 'public/generations').iterdir())), 1)
        self.assertEqual(list((self.output / 'private/work').iterdir()), [])

    def test_cleanup_keeps_only_current_despite_orphans_or_clock_changes(self):
        (self.logs / 'access.log').write_text(line())
        self.assertEqual(self.cli(), 0)
        previous = (self.output / 'public/current').resolve()
        generations = self.output / 'public/generations'
        for name in ('orphan-one', 'orphan-two'):
            path = generations / name
            path.mkdir()
            os.utime(path, (2000000000, 2000000000))
        self.assertEqual(self.cli(), 0)
        self.assertFalse(previous.is_dir())
        self.assertTrue((self.output / 'public/current/custom.html').is_file())
        self.assertEqual(len(list(generations.iterdir())), 1)

    def test_all_scopes_share_a_valid_generation_timestamp(self):
        (self.logs / 'access.log').write_text(line())
        args = ['--log-dir', str(self.logs), '--output-root', str(self.output), '--assets-dir', str(self.assets), '--to', '2026-10-03']
        self.assertEqual(report.main(args), 0)
        reports = [json.loads(path.read_text()) for path in (self.output / 'public/current').glob('*.json')]
        timestamps = {item['generated_at'] for item in reports}
        self.assertEqual(len(timestamps), 1)
        self.assertEqual(len(reports), 7)
        self.assertIsNotNone(dt.datetime.fromisoformat(timestamps.pop()).tzinfo)

    def test_plain_and_gzip_archives_stream_to_identical_results_without_unpacking(self):
        text = line() * 20 + line('2026-10-02T10:00:00+00:00') * 15
        plain = self.analyse(text)
        (self.logs / 'access.log').unlink()
        with gzip.open(self.logs / 'access.log-20261001.gz', 'wt') as handle:
            handle.write(text)
        compressed = report.analyse(self.logs, [self.scope()], {}, 'access.log*')[0][0]
        self.assertEqual(plain['totals'], compressed['totals'])
        self.assertEqual(plain['rows'], compressed['rows'])
        self.assertEqual([path.name for path in self.logs.iterdir()], ['access.log-20261001.gz'])

    def test_corrupt_gzip_keeps_ready_reports_and_logs_failure(self):
        (self.logs / 'access.log').write_text(line())
        self.assertEqual(self.cli(), 0)
        previous = (self.output / 'public/current').resolve()
        (self.logs / 'access.log-broken.gz').write_bytes(b'not a gzip archive')
        self.assertEqual(self.cli(), 1)
        self.assertEqual((self.output / 'public/current').resolve(), previous)
        self.assertIn('BadGzipFile', (self.output / 'private/report.log').read_text())

    def test_missing_classification_file_does_not_silently_reclassify_traffic(self):
        (self.logs / 'access.log').write_text(line())
        self.assertEqual(self.cli(), 0)
        previous = (self.output / 'public/current').resolve()
        self.assertEqual(self.cli(['--classifications', str(self.root / 'missing.json')]), 1)
        self.assertEqual((self.output / 'public/current').resolve(), previous)

    def test_invalid_job_settings_errors_are_written_to_the_same_log(self):
        settings = self.output / 'private/settings.json'
        settings.write_text('invalid JSON')
        self.assertEqual(report.main(['--settings', str(settings)]), 1)
        self.assertIn('JSONDecodeError', (self.output / 'private/report.log').read_text())

    def test_snapshot_ignores_later_appends(self):
        path = self.logs / 'access.log'
        path.write_text(line())
        records = report.read_logs(self.logs, 'access.log*', collections.Counter())
        next(records)
        with path.open('a') as handle:
            handle.write(line())
        self.assertEqual(list(records), [])

    def test_snapshot_rejects_truncation(self):
        path = self.logs / 'access.log'
        path.write_text(line() * 2)
        records = report.read_logs(self.logs, 'access.log*', collections.Counter())
        next(records)
        path.write_text('')
        with self.assertRaises(RuntimeError):
            list(records)

    def test_html_payload_cannot_close_script_element(self):
        entry = line().replace('1.524', '</script><script>alert(1)</script>')
        (self.logs / 'access.log').write_text(entry)
        self.assertEqual(self.cli(), 0)
        text = (self.output / 'public/current/custom.html').read_text()
        self.assertNotIn('</script><script>alert(1)</script>', text)

    def test_runtime_keeps_settings_and_cron_in_analytics_folder(self):
        cron = self.root / 'cron-entry'
        hashed = types.SimpleNamespace(stdout='admin:$2y$05$synthetic\n')
        with mock.patch.dict(os.environ, {'MAP_REPORTS_ENABLED': 'true'}, clear=True), mock.patch.object(runtime.grp, 'getgrnam', return_value=types.SimpleNamespace(gr_gid=os.getgid())), mock.patch.object(runtime.subprocess, 'run', return_value=hashed):
            runtime.prepare(self.output, cron)
        self.assertTrue(cron.is_symlink())
        self.assertEqual(cron.resolve(), self.output / 'private/cron.conf')
        settings = json.loads((self.output / 'private/settings.json').read_text())
        self.assertTrue(settings['enabled'])
        self.assertNotIn('password', settings)
        self.assertIn('auth_basic', (self.output / 'private/nginx.conf').read_text())

    def test_invalid_auth_configuration_fails_closed(self):
        cron = self.root / 'cron-entry'
        with mock.patch.dict(os.environ, {'MAP_REPORTS_ENABLED': 'true', 'MAP_REPORTS_USER': 'bad:name'}, clear=True), mock.patch.object(runtime.grp, 'getgrnam', return_value=types.SimpleNamespace(gr_gid=os.getgid())):
            runtime.prepare(self.output, cron)
        self.assertFalse(json.loads((self.output / 'private/settings.json').read_text())['enabled'])
        self.assertNotIn('alias', (self.output / 'private/nginx.conf').read_text())
        self.assertFalse(cron.exists())


if __name__ == '__main__':
    unittest.main()
