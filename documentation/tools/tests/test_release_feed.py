"""Public artifact bindings and update publication use fully synthetic releases."""

import json
from pathlib import Path
import tempfile
import unittest

from documentation.tools.website.release_feed import load_registry, register_artifact, release_feed
from documentation.tools.website.release_notes import load_releases


class ReleaseFeedTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="mtl-release-feed-")
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.notes = self.root / "documentation/release-notes"
        self.notes.mkdir(parents=True)
        self.write_note("0.9.0", "null")

    def write_note(self, version, date):
        (self.notes / f"v.{version}.md").write_text(
            f"---\nreleased_on: {date}\nteaser: >-\n  Public summary.\n---\n\n# MTL Explorer {version}\n\n## Changes\n\nDetails.\n")

    def record(self, build="1.508"):
        return {"buildId": build, "image": f"example/mtl:{build}", "digest": "sha256:" + "a" * 64,
                "revision": "private-source-revision", "tree": "private-tree"}

    def feed(self):
        return release_feed(self.root, load_releases(self.notes), "https://mtl-explorer.com")

    def test_upcoming_notes_never_offer_an_update(self):
        register_artifact(self.root, "0.9.0", self.record())
        self.assertEqual(self.feed(), {"schemaVersion": 1, "channels": {}, "releases": {}})

    def test_published_release_binds_notes_and_exact_image(self):
        register_artifact(self.root, "0.9.0", self.record())
        self.write_note("0.9.0", "2026-10-03")
        feed = self.feed()
        self.assertEqual(feed["channels"], {"stable": "0.9.0"})
        entry = feed["releases"]["0.9.0"]
        self.assertEqual(entry["buildId"], "1.508")
        self.assertEqual(entry["summary"], "Public summary.")
        self.assertEqual(entry["releaseNotesUrl"], "https://mtl-explorer.com/releases/0.9.0/")
        self.assertNotIn("private", json.dumps(feed))

    def test_release_binding_is_immutable_and_idempotent(self):
        register_artifact(self.root, "0.9.0", self.record())
        register_artifact(self.root, "0.9.0", self.record())
        with self.assertRaises(ValueError):
            register_artifact(self.root, "0.9.0", self.record("1.509"))

    def test_mismatched_build_identity_and_invalid_digest_are_rejected(self):
        with self.assertRaises(ValueError):
            register_artifact(self.root, "0.9.0", {**self.record(), "buildId": "1.520"})
        with self.assertRaises(ValueError):
            register_artifact(self.root, "0.9.0", {**self.record(), "digest": "unverified"})

    def test_channel_advance_preserves_installed_release_mapping(self):
        self.write_note("0.9.0", "2026-10-03")
        register_artifact(self.root, "0.9.0", self.record())
        self.write_note("0.10.0", "2026-10-03")
        register_artifact(self.root, "0.10.0", self.record("1.520"))
        self.assertEqual(self.feed()["channels"]["stable"], "0.10.0")
        self.assertEqual(set(self.feed()["releases"]), {"0.9.0", "0.10.0"})

    def test_one_image_can_be_promoted_to_another_public_release(self):
        for version in ("0.9.0", "0.10.0"):
            self.write_note(version, "2026-10-03")
            register_artifact(self.root, version, self.record())
        feed = self.feed()
        self.assertEqual(feed["channels"]["stable"], "0.10.0")
        self.assertEqual(feed["releases"]["0.9.0"]["digest"], feed["releases"]["0.10.0"]["digest"])

    def bundle(self, search="2.1", compatible=None):
        app = self.record()
        components = {"app": {"buildId": "1.508", "image": app["image"], "digest": app["digest"]},
                      "location-search": {"buildId": search, "image": "example/search:" + search, "digest": "sha256:" + "b" * 64}}
        if compatible is not None:
            components["location-search"]["compatibleBuildIds"] = compatible
        return {"components": components}

    def test_component_only_release_preserves_server_image_and_public_privacy(self):
        for version, build in (("0.9.0", "2.1"), ("0.9.1", "2.2")):
            self.write_note(version, "2026-10-03")
            register_artifact(self.root, version, self.bundle(build, ["2.1"]))
        feed = self.feed()
        self.assertEqual(feed["channels"]["stable"], "0.9.1")
        self.assertEqual(feed["releases"]["0.9.0"]["buildId"], feed["releases"]["0.9.1"]["buildId"])
        self.assertEqual(feed["releases"]["0.9.1"]["components"]["location-search"]["buildId"], "2.2")
        self.assertNotIn("private", json.dumps(feed))

    def test_private_or_unsupported_components_are_rejected(self):
        for key in ("revision", "postgres"):
            record = self.bundle()
            record["components"][key] = record["components"]["app"]
            with self.assertRaises(ValueError):
                register_artifact(self.root, "0.9.0", record)
        record = self.bundle()
        record["components"]["app"]["revision"] = "private"
        with self.assertRaises(ValueError):
            register_artifact(self.root, "0.9.0", record)

    def test_build_identity_cannot_be_rebound_in_a_new_release(self):
        register_artifact(self.root, "0.9.0", self.bundle())
        record = self.bundle()
        record["components"]["location-search"]["digest"] = "sha256:" + "c" * 64
        with self.assertRaises(ValueError):
            register_artifact(self.root, "0.9.1", record)
        self.assertEqual(set(load_registry(self.root)["releases"]), {"0.9.0"})


if __name__ == "__main__":
    unittest.main()
