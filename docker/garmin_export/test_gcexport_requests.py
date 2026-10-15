"""Check the patched exporter against its installed client using mocked HTTP responses.

Run in the exporter venv with GCEXPORT_SOURCE_DIR pointing to its source directory.
"""

import importlib.util
import os
import sys
import unittest
from http import HTTPStatus
from pathlib import Path
from unittest.mock import Mock
from urllib.error import HTTPError

from requests import Request


API_BASE_URL = "https://connectapi.garmin.com"


class GarminRequestTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        source_dir = Path(os.environ["GCEXPORT_SOURCE_DIR"]).resolve()
        sys.path.insert(0, str(source_dir))
        spec = importlib.util.spec_from_file_location("mtl_gcexport_under_test", source_dir / "gcexport.py")
        cls.exporter = importlib.util.module_from_spec(spec)
        sys.modules[spec.name] = cls.exporter
        spec.loader.exec_module(cls.exporter)

    def setUp(self):
        # Keep the real client's request validation and response handling.
        self.client = self.exporter.GarminClient()
        self.client.get_api_headers = Mock(return_value={"Accept": "application/json"})
        self.response = Mock(status_code=HTTPStatus.OK, content=b"[]")
        self.response.json.return_value = {}
        self.request = Mock(return_value=self.response)
        self.client._api_session = Mock(request=self.request)
        self.exporter._garmin_client = self.client
        self.addCleanup(setattr, self.exporter, "_garmin_client", None)

    def assert_request(self, method, path, params):
        args, kwargs = self.request.call_args
        self.assertEqual((method, f"{API_BASE_URL}/{path}"), args)
        self.assertEqual(params, kwargs["params"])
        return kwargs

    def test_activity_list_passes_pagination_as_params(self):
        path = "activitylist-service/activities/search/activities"
        for prefix in (f"{API_BASE_URL}/", "https://connect.garmin.com/", "", "/"):
            with self.subTest(prefix=prefix):
                self.request.reset_mock()
                body = self.exporter.http_req(f"{prefix}{path}?start=0&limit=791")
                self.assertEqual(b"[]", body)
                self.request.assert_called_once()
                self.assert_request("GET", path, [("start", "0"), ("limit", "791")])

    def test_gear_query_and_headers_are_preserved(self):
        path = "gear-service/gear/filterGear"
        self.exporter.http_req(f"{API_BASE_URL}/{path}?activityId=123", headers={"X-Test": "value"})
        kwargs = self.assert_request("GET", path, [("activityId", "123")])
        self.assertEqual("value", kwargs["headers"]["X-Test"])

    def test_query_encoding_preserves_repeated_and_blank_values(self):
        path = "activitylist-service/activities/search/activities"
        query = "search=Z%C3%BCrich%20%26%20trail&tag=a%2Bb&tag=c&empty="
        self.exporter.http_req(f"{API_BASE_URL}/{path}?{query}")
        kwargs = self.assert_request("GET", path, [
            ("search", "Zürich & trail"), ("tag", "a+b"), ("tag", "c"), ("empty", "")
        ])
        prepared = Request("GET", f"{API_BASE_URL}/{path}", params=kwargs["params"]).prepare()
        self.assertEqual(
            f"{API_BASE_URL}/{path}?search=Z%C3%BCrich+%26+trail&tag=a%2Bb&tag=c&empty=",
            prepared.url,
        )

    def test_post_keeps_query_separate_from_body(self):
        path = "activity-service/activity/123"
        for body, expected in (({"name": "Test ride"}, b"name=Test+ride"), (b"raw-body", b"raw-body")):
            with self.subTest(body=body):
                self.exporter.http_req(f"{API_BASE_URL}/{path}?mode=test", post=body)
                kwargs = self.assert_request("POST", path, [("mode", "test")])
                self.assertEqual(expected, kwargs["data"])

    def test_get_without_query_preserves_response(self):
        path = "activity-service/activity/123"
        self.assertEqual(b"[]", self.exporter.http_req(f"{API_BASE_URL}/{path}"))
        self.assert_request("GET", path, [])

    def test_download_preserves_binary_response_and_query(self):
        path = "download-service/export/gpx/activity/123"
        self.response.content = b"<gpx/>"
        self.assertEqual(b"<gpx/>", self.exporter.http_req(f"{API_BASE_URL}/{path}?format=gpx"))
        kwargs = self.assert_request("GET", path, [("format", "gpx")])
        self.assertEqual("*/*", kwargs["headers"]["Accept"])

    def test_empty_download_returns_empty_bytes(self):
        self.response.status_code = HTTPStatus.NO_CONTENT
        self.assertEqual(b"", self.exporter.http_req(f"{API_BASE_URL}/download-service/export/gpx/activity/123"))

    def test_not_found_is_translated_to_http_error(self):
        self.response.status_code = HTTPStatus.NOT_FOUND
        self.response.json.return_value = {"message": "Activity missing"}
        with self.assertRaises(HTTPError) as raised:
            self.exporter.http_req(f"{API_BASE_URL}/activity-service/activity/123?mode=test")
        self.assertEqual(HTTPStatus.NOT_FOUND, raised.exception.code)

    def test_client_still_rejects_invalid_path(self):
        with self.assertRaisesRegex(ValueError, "Invalid API path"):
            self.exporter.http_req(f"{API_BASE_URL}/%2e%2e/activity-service/activity/123?mode=test")
        self.request.assert_not_called()


if __name__ == "__main__":
    unittest.main()
