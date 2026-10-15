import datetime as dt
from pathlib import Path
import tempfile
import unittest

from test_report import report
from demo import seed_logs


class DemoTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)

    def tearDown(self):
        for handler in report.LOGGER.handlers:
            handler.close()
        report.LOGGER.handlers.clear()
        self.temp.cleanup()

    def test_unowned_logs_are_preserved(self):
        logs = self.root / 'logs'
        logs.mkdir()
        original = logs / 'access.log'
        original.write_text('existing input\n')
        with self.assertRaisesRegex(ValueError, 'unowned folder'):
            seed_logs.seed(logs, self.root / 'analytics', dt.datetime.now(report.UTC), days=7)
        self.assertEqual(original.read_text(), 'existing input\n')

    def test_owned_demo_reseeds_mixed_archives_and_matches_expected_totals(self):
        logs, data = self.root / 'logs', self.root / 'analytics'
        anchor = dt.datetime(2026, 10, 27, 12, 30, tzinfo=report.ZoneInfo('Europe/Zurich'))
        for _ in range(2):
            expected = seed_logs.seed(logs, data, anchor, days=10)
            self.assertEqual(len(list(logs.glob('access.log*.gz'))), 7)
            self.assertEqual(len(list(logs.glob('access.log*'))), 11)
            known = report.load_classifications(data / 'private/classifications.json', [])
            args = report.argparse.Namespace(from_date=None, to_date=anchor.isoformat(), days=None, group=None)
            scopes = report.make_scopes(args, anchor.tzinfo, anchor)
            results = report.analyse(logs, scopes, known, 'access.log*')[0]
            self.assertEqual({item['name'] for item in results}, set(expected))
            for result in results:
                oracle = expected[result['name']]
                self.assertEqual(result['totals']['all']['requests'], oracle['requests'])
                self.assertEqual(result['totals']['all']['bytes'], oracle['bytes'])
                self.assertEqual(result['totals']['candidate']['installations'], oracle['candidate_installations'])
                for category in report.CATEGORIES:
                    self.assertEqual(result['totals'][category]['requests'], oracle['categories'][category]['requests'])


if __name__ == '__main__':
    unittest.main()
