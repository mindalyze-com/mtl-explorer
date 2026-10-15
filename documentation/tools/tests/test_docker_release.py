"""Release source isolation and publication scope checks; no Docker required."""

import importlib.util
import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


SCRIPT = Path(__file__).resolve().parents[3] / "docker-build-deploy.py"
SPEC = importlib.util.spec_from_file_location("mtl_docker_release", SCRIPT)
release = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = release
SPEC.loader.exec_module(release)


class ReleaseSourceTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="mtl-release-test-")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name) / "mtl-explorer"
        self.root.mkdir()
        self.git("init", "--quiet")
        self.git("config", "user.name", "Release Test")
        self.git("config", "user.email", "release@example.invalid")
        (self.root / "app.txt").write_text("committed source\n")
        self.git("add", "app.txt")
        self.git("commit", "--quiet", "-m", "Initial source")

    def git(self, *args):
        return subprocess.check_output(["git", *args], cwd=self.root, text=True).strip()

    def test_snapshot_uses_pinned_commit_after_workspace_changes(self):
        identity = release.app_source_identity(self.root)
        (self.root / "app.txt").write_text("later edit\n")
        (self.root / "untracked.txt").write_text("not release source\n")
        with release.app_source_snapshot(identity["revision"], self.root) as snapshot:
            self.assertEqual((snapshot / "app.txt").read_text(), "committed source\n")
            self.assertFalse((snapshot / "untracked.txt").exists())
        self.assertFalse(snapshot.exists())
        self.assertEqual(identity["tree"], self.git("rev-parse", "HEAD^{tree}"))

    def test_tracked_edits_require_a_commit(self):
        (self.root / "app.txt").write_text("uncommitted source\n")
        with self.assertRaisesRegex(SystemExit, "app.txt"):
            release.app_source_identity(self.root)

    def test_untracked_source_requires_a_commit(self):
        (self.root / "new.py").write_text("print('new source')\n")
        with self.assertRaisesRegex(SystemExit, "new.py"):
            release.app_source_identity(self.root)

    def test_active_regression_evidence_does_not_block_or_enter_snapshot(self):
        evidence = self.root.parent / "mtl-explorer-private/docs-my/verification/regression/full-suite/current"
        evidence.mkdir(parents=True)
        (evidence / "run-state.md").write_text("IN PROGRESS\n")
        identity = release.app_source_identity(self.root)
        with release.app_source_snapshot(identity["revision"], self.root) as snapshot:
            self.assertFalse((snapshot / "docs-my").exists())


class ReleasePublicationTests(unittest.TestCase):
    def settings(self, *arguments):
        with patch.object(sys, "argv", [str(SCRIPT), "--yes", "--app-only", *arguments]):
            return release.release_settings_from_draft(release.initial_settings_draft(release.parse_args()))

    def test_app_only_publishes_version_and_alpha_with_source_metadata(self):
        settings = self.settings()
        images = release.image_configs(settings)
        self.assertEqual([image.image_name for image in images], [release.APP_IMAGE_NAME])
        refs = release.all_remote_refs(settings)
        self.assertEqual(refs, [f"wauwau0977/mytraillog:{release.APP_VERSION_TAG}",
                                "wauwau0977/mytraillog:alpha"])
        runner = release.DockerReleaseRunner(settings)
        runner.app_source = {"revision": "a" * 40, "tree": "b" * 40}
        cmd = runner.buildx_command(images[0])
        self.assertIn("org.opencontainers.image.revision=" + "a" * 40, cmd)
        self.assertIn("MTL_IMAGE_REVISION=" + "a" * 40, cmd)
        self.assertIn("index,manifest:org.opencontainers.image.revision=" + "a" * 40, cmd)
        self.assertIn("--provenance=mode=min", cmd)
        self.assertEqual(cmd[cmd.index("--platform") + 1], "linux/amd64,linux/arm64")

    def test_image_build_does_not_require_a_public_release_version(self):
        with tempfile.TemporaryDirectory() as temporary:
            context = Path(temporary)
            runner = release.DockerReleaseRunner(self.settings())
            runner.app_source = {"revision": "a" * 40, "tree": "b" * 40}
            image = release.ImageConfig("App", release.APP_IMAGE_NAME, "1.530", context)
            cmd = runner.buildx_command(image)
            self.assertIn("org.opencontainers.image.version=1.530", cmd)
            self.assertIn("MTL_IMAGE_VERSION=1.530", cmd)
            self.assertFalse(any("mtl.release.version" in str(value) for value in cmd))

    def test_existing_version_is_rejected(self):
        runner = release.DockerReleaseRunner(self.settings())
        with patch.object(runner, "run_step", return_value=subprocess.CompletedProcess([], 0, "manifest", "")):
            with self.assertRaisesRegex(SystemExit, "already exists"):
                runner.ensure_app_version_unused()

    def test_registry_failure_does_not_look_like_an_unused_version(self):
        runner = release.DockerReleaseRunner(self.settings())
        with patch.object(runner, "run_step", return_value=subprocess.CompletedProcess([], 1, "", "unauthorized")):
            with self.assertRaisesRegex(SystemExit, "Could not verify"):
                runner.ensure_app_version_unused()

    def test_missing_manifest_allows_a_new_version(self):
        runner = release.DockerReleaseRunner(self.settings())
        message = f"ERROR: docker.io/wauwau0977/mytraillog:{release.APP_VERSION_TAG}: not found"
        with patch.object(runner, "run_step", return_value=subprocess.CompletedProcess([], 1, "", message)):
            runner.ensure_app_version_unused()

    def test_missing_credential_helper_does_not_allow_publication(self):
        runner = release.DockerReleaseRunner(self.settings())
        message = "docker-credential-example: executable file not found"
        with patch.object(runner, "run_step", return_value=subprocess.CompletedProcess([], 1, "", message)):
            with self.assertRaisesRegex(SystemExit, "Could not verify"):
                runner.ensure_app_version_unused()

    def test_bundle_record_contains_only_verified_public_component_artifacts(self):
        with tempfile.TemporaryDirectory() as temporary:
            runner = release.DockerReleaseRunner(self.settings("--release-record-dir", temporary))
            runner.app_release = {"digest": "sha256:" + "a" * 64, "revision": "private"}
            with patch.object(runner, "run_step", return_value=subprocess.CompletedProcess([], 0, "sha256:" + "a" * 64, "")):
                runner.write_bundle_record()
            record = json.loads((Path(temporary) / f"mtl-bundle-{runner.settings.app_version_tag}.json").read_text())
            self.assertEqual(set(record), {"components"})
            self.assertEqual(set(record["components"]), {"app"})
            self.assertNotIn("private", json.dumps(record))

    def test_reused_sidecar_build_tag_is_rejected(self):
        runner = release.DockerReleaseRunner(self.settings())
        image = release.ImageConfig("Search", release.LOCATION_SEARCH_IMAGE_NAME, "2.1", SCRIPT.parent)
        with patch.object(runner, "run_step", return_value=subprocess.CompletedProcess([], 0, "manifest", "")):
            with self.assertRaisesRegex(SystemExit, "already exists"):
                runner.ensure_image_version_unused(image)


class ReleaseRecordTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="mtl-release-records-")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()
        self.records = self.root / "records"

    def settings(self, *arguments):
        with patch.object(sys, "argv", [str(SCRIPT), "--yes", "--app-only", *arguments]):
            return release.release_settings_from_draft(release.initial_settings_draft(release.parse_args()))

    def runner(self, *arguments):
        return release.DockerReleaseRunner(self.settings("--release-record-dir", str(self.records), *arguments))

    def test_default_records_belong_to_separate_private_checkout(self):
        private = self.root / "private"
        (private / ".git").mkdir(parents=True)
        with patch.dict(os.environ, {"MTL_PRIVATE_REPO": str(private)}):
            settings = self.settings()
        self.assertEqual(settings.release_record_dir, private / "docs-my/operations/releases/builds")
        self.assertFalse(settings.release_record_dir.exists())

    def test_missing_private_checkout_stops_without_creating_it(self):
        private = self.root / "missing"
        with patch.dict(os.environ, {"MTL_PRIVATE_REPO": str(private)}):
            with self.assertRaisesRegex(SystemExit, "supply --release-record-dir"):
                self.settings()
        self.assertFalse(private.exists())

    def test_explicit_directory_works_without_a_private_checkout(self):
        with patch.dict(os.environ, {"MTL_PRIVATE_REPO": str(self.root / "missing")}):
            runner = self.runner()
        runner.prepare_release_record_directory()
        record = runner.write_release_record("image.json", {"digest": "sha256:" + "a" * 64})
        self.assertEqual(record, self.records / "image.json")
        self.assertEqual(json.loads(record.read_text()), {"digest": "sha256:" + "a" * 64})
        self.assertEqual(runner.release_records, [record])

    def test_application_checkout_and_symlinks_into_it_are_rejected(self):
        alias = self.root / "source-alias"
        alias.symlink_to(SCRIPT.parent, target_is_directory=True)
        for directory in (SCRIPT.parent / "release-artifacts", alias / "release-artifacts"):
            with self.assertRaisesRegex(SystemExit, "outside the application checkout"):
                self.settings("--release-record-dir", str(directory))

    def test_tag_only_does_not_require_record_storage(self):
        with patch.dict(os.environ, {"MTL_PRIVATE_REPO": str(self.root / "missing")}):
            settings = self.settings("--tag-latest-only")
        self.assertIsNone(settings.release_record_dir)

    def test_dry_run_never_creates_record_directory(self):
        runner = self.runner("--dry-run", "--no-login")
        with patch.object(release, "app_source_identity", return_value={"revision": "a" * 40, "tree": "b" * 40}), \
                patch.object(release.subprocess, "run") as external:
            runner.run()
        external.assert_not_called()
        self.assertFalse(self.records.exists())

    def test_app_and_bundle_records_use_the_selected_persistent_directory(self):
        runner = self.runner()
        runner.app_source = {"revision": "a" * 40, "tree": "b" * 40}
        digest = "sha256:" + "c" * 64
        source = self.root / "snapshot"
        source.mkdir()
        (source.parent / "build-metadata.json").write_text(json.dumps({"containerimage.digest": digest}))
        runner.prepare_release_record_directory()
        with patch.object(release, "app_source_snapshot") as snapshot, \
                patch.object(runner, "run_step", return_value=subprocess.CompletedProcess([], 0, digest, "")):
            snapshot.return_value.__enter__.return_value = source
            runner.build_app_image()
            runner.write_bundle_record()
        app = self.records / f"mytraillog-{runner.settings.app_version_tag}.json"
        bundle = self.records / f"mtl-bundle-{runner.settings.app_version_tag}.json"
        self.assertEqual(json.loads(app.read_text())["revision"], runner.app_source["revision"])
        self.assertEqual(json.loads(app.read_text())["digest"], digest)
        self.assertEqual(json.loads(bundle.read_text())["components"]["app"]["digest"], digest)
        self.assertEqual(runner.release_records, [app, bundle])

    def test_existing_record_stops_before_docker_and_is_preserved(self):
        for filename in (f"mytraillog-{release.APP_VERSION_TAG}.json", f"mtl-bundle-{release.APP_VERSION_TAG}.json"):
            with self.subTest(filename=filename):
                self.records.mkdir(exist_ok=True)
                existing = self.records / filename
                existing.write_text("original record\n")
                runner = self.runner()
                with patch.object(release, "app_source_identity", return_value={"revision": "a" * 40, "tree": "b" * 40}), \
                        patch.object(runner, "ensure_docker_daemon") as docker:
                    with self.assertRaisesRegex(SystemExit, "Release record already exists"):
                        runner.run_full_build()
                docker.assert_not_called()
                self.assertEqual(existing.read_text(), "original record\n")
                existing.unlink()

    def test_unwritable_record_destination_stops_before_docker(self):
        self.records.write_text("not a directory\n")
        runner = self.runner()
        with patch.object(release, "app_source_identity", return_value={"revision": "a" * 40, "tree": "b" * 40}), \
                patch.object(runner, "ensure_docker_daemon") as docker:
            with self.assertRaisesRegex(SystemExit, "Cannot write release records"):
                runner.run_full_build()
        docker.assert_not_called()

    def test_record_created_after_preflight_is_never_overwritten(self):
        runner = self.runner()
        runner.prepare_release_record_directory()
        record = self.records / "image.json"
        record.write_text("original record\n")
        with self.assertRaises(FileExistsError):
            runner.write_release_record(record.name, {"digest": "replacement"})
        self.assertEqual(record.read_text(), "original record\n")


if __name__ == "__main__":
    unittest.main()
