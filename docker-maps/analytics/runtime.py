#!/usr/bin/env python3
"""Configure static report authentication and cron without an analytics server."""
import argparse
import grp
import json
import os
from pathlib import Path
import subprocess
import sys

import report

DATA_ROOT = Path('/app/analytics/data')
CRON_FILE = Path('/etc/cron.d/mtl-map-reports')
DEFAULT_USER = 'admin'
DEFAULT_PASSWORD = 'change-me'
VALID_INTERVALS = (1, 2, 3, 4, 6, 8, 12, 24)
DISABLED_LOCATION = 'location = /reports { return 404; }\nlocation ^~ /reports/ { return 404; }\n'
ENABLED_LOCATION = '''location = /reports { return 302 /reports/; }
location ^~ /reports/ {
    auth_basic "MTL Explorer map reports";
    auth_basic_user_file /app/analytics/data/private/htpasswd;
    alias /app/analytics/data/public/current/;
    index index.html;
    autoindex off;
    access_log off;
    add_header Cache-Control "no-cache" always;
    limit_except GET HEAD { deny all; }
}
'''


def write_text(path, text, mode=0o600):
    temporary = path.with_name(path.name + '.tmp')
    temporary.write_text(text)
    temporary.chmod(mode)
    os.replace(temporary, path)


def prepare(root=DATA_ROOT, cron_file=CRON_FILE):
    root.mkdir(parents=True, exist_ok=True, mode=0o755)
    root.chmod(0o755)
    report.configure_logging(root)
    private = root / 'private'
    location = private / 'nginx.conf'
    settings_file = private / 'settings.json'
    settings = {'enabled': False, 'output_root': str(root), 'log_dir': '/data/logs',
                'timezone': os.environ.get('MAP_REPORTS_TIMEZONE', 'Europe/Zurich'),
                'classifications': str(private / 'classifications.json')}
    try:
        group = grp.getgrnam('www-data').gr_gid
        os.chown(private, -1, group)
        private.chmod(0o750)
        enabled = os.environ.get('MAP_REPORTS_ENABLED', 'false').lower()
        if enabled not in ('true', 'false', '1', '0'):
            raise ValueError('MAP_REPORTS_ENABLED must be true or false')
        if enabled in ('true', '1'):
            interval = int(os.environ.get('MAP_REPORTS_INTERVAL_HOURS', '8'))
            if interval not in VALID_INTERVALS:
                raise ValueError('MAP_REPORTS_INTERVAL_HOURS must divide 24')
            report.ZoneInfo(settings['timezone'])
            user = os.environ.get('MAP_REPORTS_USER', DEFAULT_USER)
            password = os.environ.get('MAP_REPORTS_PASSWORD', DEFAULT_PASSWORD)
            if not user or ':' in user or any(c in user + password for c in '\r\n\x00') or not password:
                raise ValueError('Report credentials must be non-empty single-line values; username cannot contain a colon')
            result = subprocess.run(['htpasswd', '-nBi', user], input=password + '\n', capture_output=True, text=True, check=True)
            write_text(private / 'htpasswd', result.stdout, 0o640)
            os.chown(private / 'htpasswd', -1, group)
            classifications = private / 'classifications.json'
            if not classifications.exists():
                report.atomic_json(classifications, {'installations': {}})
            public = root / 'public'
            public.mkdir(exist_ok=True, mode=0o755)
            current = public / 'current'
            if not current.exists():
                waiting = public / 'generations' / 'waiting'
                waiting.mkdir(parents=True, mode=0o755, exist_ok=True)
                write_text(waiting / 'index.html', '<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>MTL Explorer map reports</title><h1>MTL Explorer map reports</h1><p>The first report is being prepared. Refresh this page after the job finishes.</p></html>', 0o644)
                current.unlink(missing_ok=True)
                current.symlink_to('generations/waiting', target_is_directory=True)
            settings['enabled'] = True
            write_text(location, ENABLED_LOCATION)
            hours = '0' if interval == 24 else '*/' + str(interval)
            command = f'/usr/bin/python3 /app/analytics/report.py --settings {settings_file} >/proc/1/fd/1 2>&1'
            crontab = private / 'cron.conf'
            write_text(crontab, f'SHELL=/bin/sh\nPATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin\n0 {hours} * * * root {command}\n', 0o644)
            cron_file.unlink(missing_ok=True)
            cron_file.symlink_to(crontab)
            report.LOGGER.info('Report runtime configured: interval_hours=%d timezone=%s path=/reports/', interval, settings['timezone'])
        else:
            write_text(location, DISABLED_LOCATION)
            cron_file.unlink(missing_ok=True)
            report.LOGGER.info('Static map reports disabled')
    except Exception as error:
        report.LOGGER.exception('Report configuration failed: type=%s; reports disabled', type(error).__name__)
        settings['enabled'] = False
        write_text(location, DISABLED_LOCATION)
        cron_file.unlink(missing_ok=True)
    report.atomic_json(settings_file, settings)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('prepare', 'launch', 'rotate'), nargs='?', default='prepare')
    args = parser.parse_args()
    if args.action == 'prepare':
        prepare()
        return 0
    if args.action == 'rotate':
        report.configure_logging(DATA_ROOT)
        try:
            with report.run_lock(DATA_ROOT / 'private/report.lock', 'Map log rotation') as acquired:
                if acquired:
                    report.LOGGER.info('Map log rotation started')
                    subprocess.run(['/usr/sbin/logrotate', '-s', '/data/logs/logrotate.status', '/etc/logrotate.d/mtl-maps'], check=True)
                    report.LOGGER.info('Map log rotation completed')
            return 0
        except Exception as error:
            report.LOGGER.exception('Map log rotation failed: type=%s', type(error).__name__)
            return 1
    return report.main(['--settings', str(DATA_ROOT / 'private/settings.json')])


if __name__ == '__main__':
    sys.exit(main())
