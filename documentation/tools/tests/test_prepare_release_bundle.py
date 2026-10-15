"""Bundle preparation uses synthetic artifacts and mocked registry access."""

from copy import deepcopy
import subprocess
import unittest
from unittest.mock import patch

from documentation.tools.website.prepare_release_bundle import prepare_bundle


class PrepareReleaseBundleTests(unittest.TestCase):
    def setUp(self):
        self.base = {"components": {
            "app": self.artifact("mtl", "1.520"),
            "location-search": self.artifact("search", "2.1"),
            "brouter": self.artifact("brouter", "1.24"),
        }}

    def artifact(self, image, build):
        return {"buildId": build, "image": f"example/{image}:{build}", "digest": "sha256:" + "a" * 64}

    def test_search_only_update_reuses_app_and_other_components(self):
        original = deepcopy(self.base)
        with patch("documentation.tools.website.prepare_release_bundle.subprocess.run", return_value=subprocess.CompletedProcess([], 0, "sha256:" + "b" * 64)) as registry:
            bundle = prepare_bundle(self.base, {"location-search": "example/search:2.2"})
        self.assertEqual(bundle["components"]["app"], self.base["components"]["app"])
        self.assertEqual(bundle["components"]["brouter"], self.base["components"]["brouter"])
        self.assertEqual(bundle["components"]["location-search"]["buildId"], "2.2")
        self.assertEqual(bundle["components"]["location-search"]["digest"], "sha256:" + "b" * 64)
        self.assertEqual(self.base, original)
        registry.assert_called_once()

    def test_app_only_update_retains_explicit_base_components(self):
        with patch("documentation.tools.website.prepare_release_bundle.subprocess.run", return_value=subprocess.CompletedProcess([], 0, "sha256:" + "b" * 64)):
            bundle = prepare_bundle(self.base, {"app": "example/mtl:1.521"})
        self.assertEqual(bundle["components"]["app"]["buildId"], "1.521")
        self.assertEqual(bundle["components"]["location-search"], self.base["components"]["location-search"])

    def test_bad_digest_or_rebound_build_is_rejected(self):
        for image, digest in (("example/search:2.2", "invalid"), ("example/search:2.1", "sha256:" + "b" * 64)):
            with self.subTest(image=image), patch("documentation.tools.website.prepare_release_bundle.subprocess.run", return_value=subprocess.CompletedProcess([], 0, digest)):
                with self.assertRaises(ValueError):
                    prepare_bundle(self.base, {"location-search": image})

    def test_unknown_component_and_moving_tags_are_rejected_before_network(self):
        with patch("documentation.tools.website.prepare_release_bundle.subprocess.run") as registry:
            for replacements in ({"unknown": "example/mtl:1.2"}, {"app": "example/mtl:latest"}, {"app": "example/mtl"}):
                with self.assertRaises(ValueError):
                    prepare_bundle(self.base, replacements)
            registry.assert_not_called()

    def test_registry_error_does_not_produce_a_bundle(self):
        with patch("documentation.tools.website.prepare_release_bundle.subprocess.run", side_effect=subprocess.CalledProcessError(1, "docker")):
            with self.assertRaises(subprocess.CalledProcessError):
                prepare_bundle(self.base, {"location-search": "example/search:2.2"})
