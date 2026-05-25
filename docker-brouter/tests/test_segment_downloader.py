import contextlib
import io
import sys
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parents[1] / "scripts"
sys.path.insert(0, str(SCRIPT_DIR))

import segment_downloader  # noqa: E402
from segment_downloader import SegmentDownloader  # noqa: E402


class SegmentValidationTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.segments_dir = Path(self.tmp.name)
        self.original_base_url = segment_downloader.SEGMENTS_BASE_URL
        self.original_timeout = segment_downloader.DOWNLOAD_TIMEOUT_SEC
        segment_downloader.DOWNLOAD_TIMEOUT_SEC = 1
        self.server = None
        self.server_thread = None

    def tearDown(self):
        if self.server is not None:
            self.server.shutdown()
            self.server.server_close()
        if self.server_thread is not None:
            self.server_thread.join(timeout=1)
        segment_downloader.SEGMENTS_BASE_URL = self.original_base_url
        segment_downloader.DOWNLOAD_TIMEOUT_SEC = self.original_timeout
        self.tmp.cleanup()

    def test_matching_content_length_preserves_existing_file(self):
        tile = self.write_tile("E5_N45.rd5", b"valid")
        self.start_server({"E5_N45.rd5": SegmentResponse(status=200, body=b"replacement", head_length=5)})

        output = self.validate_and_capture_output()

        self.assertEqual(b"valid", tile.read_bytes())
        self.assertEqual([], SegmentDownloader(self.segments_dir).snapshot()["segmentsRepaired"])
        self.assertIn("validation complete validated=1 repaired=0 warnings=0", output)

    def test_mismatched_content_length_redownloads_and_logs_repair(self):
        tile = self.write_tile("E5_N45.rd5", b"old")
        self.start_server({"E5_N45.rd5": SegmentResponse(status=200, body=b"fresh-segment", head_length=13)})

        output = self.validate_and_capture_output()

        self.assertEqual(b"fresh-segment", tile.read_bytes())
        self.assertIn(
            "validation mismatch E5_N45.rd5 local_size=3 upstream_size=13 action=redownload",
            output,
        )
        self.assertIn("validation repaired E5_N45.rd5 bytes=13", output)

    def test_upstream_404_deletes_local_file_and_marks_known_missing(self):
        tile = self.write_tile("W125_N35.rd5", b"stale")
        self.start_server({"W125_N35.rd5": SegmentResponse(status=404)})
        downloader = SegmentDownloader(self.segments_dir)

        output = self.validate_and_capture_output(downloader)

        self.assertFalse(tile.exists())
        status = downloader.snapshot()
        self.assertEqual(1, status["segmentsKnown404"])
        self.assertIn("W125_N35.rd5", status["segmentsFailed"])
        self.assertIn("validation upstream 404 W125_N35.rd5 action=delete", output)

    def test_head_network_error_preserves_local_file_and_records_warning(self):
        tile = self.write_tile("E5_N45.rd5", b"valid")
        segment_downloader.SEGMENTS_BASE_URL = "http://127.0.0.1:9"
        downloader = SegmentDownloader(self.segments_dir)

        output = self.validate_and_capture_output(downloader)

        self.assertEqual(b"valid", tile.read_bytes())
        status = downloader.snapshot()
        self.assertEqual("complete", status["segmentsValidationPhase"])
        self.assertEqual(1, len(status["segmentsValidationWarnings"]))
        self.assertIn("validation warning E5_N45.rd5 upstream HEAD failed:", output)
        self.assertIn("keeping local file", output)

    def test_stale_partial_files_are_removed_and_logged(self):
        part = self.segments_dir / "E5_N45.rd5.part"
        part.write_bytes(b"incomplete")

        output = self.validate_and_capture_output()

        self.assertFalse(part.exists())
        self.assertIn("validation removed stale partial file E5_N45.rd5.part action=delete", output)

    def validate_and_capture_output(self, downloader=None):
        downloader = downloader or SegmentDownloader(self.segments_dir)
        stdout = io.StringIO()
        with contextlib.redirect_stdout(stdout):
            downloader.validate_existing_segments()
        return stdout.getvalue()

    def write_tile(self, name, body):
        path = self.segments_dir / name
        path.write_bytes(body)
        return path

    def start_server(self, responses):
        handler = make_handler(responses)
        self.server = HTTPServer(("127.0.0.1", 0), handler)
        self.server_thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.server_thread.start()
        host, port = self.server.server_address
        segment_downloader.SEGMENTS_BASE_URL = f"http://{host}:{port}"


class SegmentResponse:
    def __init__(self, status=200, body=b"", head_length=None):
        self.status = status
        self.body = body
        self.head_length = head_length if head_length is not None else len(body)


def make_handler(responses):
    class Handler(BaseHTTPRequestHandler):
        def do_HEAD(self):  # noqa: N802
            self.send_response_for_path(include_body=False)

        def do_GET(self):  # noqa: N802
            self.send_response_for_path(include_body=True)

        def send_response_for_path(self, include_body):
            name = self.path.rsplit("/", 1)[-1]
            response = responses.get(name, SegmentResponse(status=404))
            self.send_response(response.status)
            if response.status == 200:
                self.send_header("Content-Length", str(response.head_length))
            self.end_headers()
            if include_body and response.status == 200:
                self.wfile.write(response.body)

        def log_message(self, _format, *_args):
            return

    return Handler


if __name__ == "__main__":
    unittest.main()
