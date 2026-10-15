"""Container build selection and snapshot isolation use synthetic sources."""

from contextlib import redirect_stderr, redirect_stdout
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import MagicMock, patch

from documentation.tools import build


class ContainerBuildTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()
        self.repository = self.root / "repository"
        self.repository.mkdir()
        self.workspace = self.root / "workspace"
        self.workspace.mkdir()
        subprocess.run(["git", "init", "-q", str(self.repository)], check=True)
        for name in ("pom.xml", *build.SERVER_INPUTS[:1], *build.CLIENT_REQUIRED_INPUTS,
                     "mtl-server/src/main/java/Server.java", "mtl-client/src/Client.vue"):
            self.write(name, "source\n")
        self.write(".gitignore", "target/\nnode_modules/\ndist/\nnode/\n")
        subprocess.run(["git", "-C", str(self.repository), "add", "."], check=True)
        self.write("mtl-client/src/Client.vue", "changed source\n")
        self.write("mtl-client/src/New.vue", "new source\n")
        self.write("mtl-client/node_modules/dependency/index.js", "ignored dependency\n")
        self.write("mtl-client/dist/index.html", "stale client build\n")
        self.write("mtl-api/mtl-api-typescript-fetch/target/generated.js", "stale API build\n")
        self.write("mtl-api/mtl-api-typescript-fetch/node/node", "ignored toolchain\n")

    def write(self, name, contents):
        path = self.repository / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(contents)
        return path

    def test_default_snapshot_includes_current_client_and_server_sources(self):
        snapshot = self.workspace / "snapshot"
        snapshot.mkdir()
        hashes = build.copy_sources(self.repository, snapshot)
        self.assertIn("mtl-server/src/main/java/Server.java", hashes)
        self.assertIn("mtl-api/open-api-schema/schema.json", hashes)
        self.assertEqual((snapshot / "mtl-client/src/Client.vue").read_text(), "changed source\n")
        self.assertIn("mtl-client/src/New.vue", hashes)
        self.assertFalse(any("node_modules/" in name or "/target/" in name or "/dist/" in name
                             or "/node/" in name for name in hashes))

    def test_selected_snapshot_excludes_the_other_component(self):
        server = build.source_files(self.repository, "server")
        self.assertFalse(any(name.startswith(("mtl-api/", "mtl-client/")) for name in server))
        client = build.source_files(self.repository, "client")
        self.assertFalse(any(name.startswith("mtl-server/") for name in client))
        self.assertIn("mtl-api/open-api-schema/schema.json", client)

    def test_deleted_sources_stay_deleted_and_missing_schema_blocks_client_build(self):
        (self.repository / "mtl-client/src/Client.vue").unlink()
        self.assertNotIn("mtl-client/src/Client.vue", build.source_files(self.repository))
        (self.repository / "mtl-api/open-api-schema/schema.json").unlink()
        with self.assertRaisesRegex(ValueError, "Missing build input"):
            build.source_files(self.repository)
        build.source_files(self.repository, "server")

    def test_symlinked_client_source_is_rejected(self):
        (self.repository / "mtl-client/src/Link.vue").symlink_to(self.root / "outside.vue")
        with self.assertRaisesRegex(ValueError, "regular repository files"):
            build.source_files(self.repository)

    def run_build(self, scope, exit_codes, keep=None):
        inspection = [{"Mounts": [{"Type": "bind", "Source": str(self.workspace),
                                    "Destination": "/workspace", "RW": True}],
                       "State": {"Running": True}, "Image": "sha256:synthetic",
                       "Config": {"Image": "test-image", "Env": ["PATH=/usr/bin:/bin"]}}]
        original_git = build.git
        original_check_output = subprocess.check_output

        def check_output(command, *arguments, **options):
            if command[:2] == ["docker", "inspect"]:
                return json.dumps(inspection).encode()
            return original_check_output(command, *arguments, **options)

        def git(repository, *arguments):
            if arguments == ("rev-parse", "HEAD"):
                return b"synthetic-commit\n"
            return original_git(repository, *arguments)

        processes = []
        for code in exit_codes:
            process = MagicMock()
            process.__enter__.return_value = process
            process.stdout = io.StringIO("build output\n")
            process.wait.return_value = code
            processes.append(process)
        with patch.object(build, "EXTERNAL_DRIVE", self.root), \
                patch.object(Path, "is_mount", return_value=True), \
                patch.object(build, "git", side_effect=git), \
                patch.object(build, "verify_container_copy"), \
                patch.object(build, "subprocess", wraps=subprocess) as subprocess_tools, \
                redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
            subprocess_tools.check_output.side_effect = check_output
            subprocess_tools.Popen.side_effect = processes
            result = build.container_build(self.repository, self.workspace, "test-container",
                                           "package", ["-DskipTests"], scope, keep=keep)
        snapshot = next(self.workspace.glob(f"{scope}-package-*"))
        record = json.loads((snapshot / "build.json").read_text())
        return result, [call.args[0] for call in subprocess_tools.Popen.call_args_list], record

    def test_both_builds_api_then_client_then_server_using_snapshot_node(self):
        result, commands, record = self.run_build("both", [0, 0, 0])
        self.assertEqual(result, 0)
        directories = [command[command.index("-w") + 1] for command in commands]
        self.assertEqual([Path(directory).name for directory in directories],
                         ["mtl-api", "mtl-client", "mtl-server"])
        self.assertTrue(all(directory.startswith("/workspace/both-package-") for directory in directories))
        self.assertEqual(commands[1][commands[1].index("-e") + 1],
                         f"PATH={Path(directories[1]).parent / build.CLIENT_NODE_DIRECTORY}:/usr/bin:/bin")
        for command in commands[:2]:
            self.assertIn("NPM_CONFIG_CACHE=/workspace/.npm-cache", command)
        self.assertEqual(commands[2][-2:], ["package", "-DskipTests"])
        self.assertEqual(record["exit_code"], 0)
        self.assertEqual(record["scope"], "both")
        self.assertFalse(record["disposable"])

    def test_keep_opt_in_marks_snapshots_disposable(self):
        result, _, record = self.run_build("server", [0], keep=2)
        self.assertEqual(result, 0)
        self.assertTrue(record["disposable"])
        self.assertEqual(record["source_repository"], str(self.repository))

    def test_failure_in_client_stops_before_server_and_is_recorded(self):
        result, commands, record = self.run_build("both", [0, 7])
        self.assertEqual(result, 7)
        self.assertEqual(len(commands), 2)
        self.assertEqual(record["exit_code"], 7)

    def test_pruning_failure_keeps_successful_build_result(self):
        old = self.workspace / "server-package-old"
        old.mkdir(parents=True)
        (old / "build.json").write_text(json.dumps({
            "source_repository": str(self.repository), "scope": "server", "goal": "package",
            "exit_code": 0, "disposable": True,
        }))
        os.utime(old / "build.json", (1, 1))
        with patch.object(build.shutil, "rmtree", side_effect=PermissionError("synthetic denied")) as remove:
            result, _, record = self.run_build("server", [0], keep=1)
        self.assertEqual(result, 0)
        self.assertEqual(record["exit_code"], 0)
        remove.assert_called_once_with(old)
        self.assertTrue(old.is_dir())

    def test_pruning_directory_read_failure_keeps_successful_build_result(self):
        original_iterdir = Path.iterdir

        def iterdir(path):
            if path == self.workspace:
                raise PermissionError("synthetic directory read failure")
            return original_iterdir(path)

        with patch.object(Path, "iterdir", iterdir):
            result, _, record = self.run_build("server", [0], keep=1)
        self.assertEqual(result, 0)
        self.assertEqual(record["exit_code"], 0)

    def test_single_component_build_runs_only_its_required_steps(self):
        for scope, codes, steps in (("server", [0], ["mtl-server"]),
                                    ("client", [0, 0], ["mtl-api", "mtl-client"])):
            with self.subTest(scope=scope):
                result, commands, record = self.run_build(scope, codes)
                self.assertEqual(result, 0)
                self.assertEqual([Path(command[command.index("-w") + 1]).name for command in commands], steps)
                self.assertEqual(commands[-1][-2:], ["package", "-DskipTests"])
                self.assertEqual(record["scope"], scope)

    def test_cli_defaults_to_both_and_flags_are_mutually_exclusive(self):
        for flag, scope in (([], "both"), (["--client-only"], "client"), (["--server-only"], "server")):
            with self.subTest(scope=scope), patch.object(build, "container_build", return_value=0) as run:
                self.assertEqual(build.main([*flag, "package", "--", "-DskipTests"]), 0)
                self.assertEqual(run.call_args.kwargs["scope"], scope)
                self.assertEqual(run.call_args.args[-1], ["-DskipTests"])
        with redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
            build.main(["--client-only", "--server-only", "package"])

    def test_cli_passes_keep_and_rejects_it_for_docs(self):
        with patch.object(build, "container_build", return_value=0) as run:
            self.assertEqual(build.main(["--keep", "3", "package"]), 0)
            self.assertEqual(run.call_args.kwargs["keep"], 3)
        with redirect_stderr(io.StringIO()):
            self.assertEqual(build.main(["--keep", "3", "docs"]), 2)


class SnapshotRetentionTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()
        self.repository = self.root / "repository"
        self.workspace = self.root / "workspace"
        self.workspace.mkdir()
        self.timestamp = 0

    def snapshot(self, name, **overrides):
        path = self.workspace / name
        path.mkdir()
        record = {"source_repository": str(self.repository), "scope": "both", "goal": "package",
                  "exit_code": 0, "disposable": True, **overrides}
        manifest = path / "build.json"
        manifest.write_text(json.dumps(record))
        self.timestamp += 1
        os.utime(manifest, (self.timestamp, self.timestamp))
        return path

    def test_cleanup_keeps_recent_successes_and_protects_evidence_and_active_builds(self):
        old = self.snapshot("both-package-old")
        recent = self.snapshot("both-package-recent")
        newest = self.snapshot("both-package-newest")
        protected = [
            self.snapshot("both-package-evidence", disposable=False),
            self.snapshot("both-package-legacy"),
            self.snapshot("both-package-running", exit_code=None),
            self.snapshot("both-package-failed", exit_code=1),
            self.snapshot("both-package-other-checkout", source_repository=str(self.root / "other")),
            self.snapshot("server-test-other-component", scope="server", goal="test"),
            self.snapshot("unrecognized-directory"),
            self.snapshot("both-package-pinned"),
            self.snapshot("both-package-corrupt"),
        ]
        legacy = protected[1] / "build.json"
        legacy_record = json.loads(legacy.read_text())
        del legacy_record["disposable"]
        legacy.write_text(json.dumps(legacy_record))
        (protected[-2] / ".keep").touch()
        (protected[-1] / "build.json").write_text("incomplete JSON")
        outside = self.root / "outside"
        outside.mkdir()
        linked = self.workspace / "both-package-link"
        linked.symlink_to(outside, target_is_directory=True)

        with redirect_stdout(io.StringIO()):
            build.prune_snapshots(self.repository, self.workspace, "both", keep=2)

        self.assertFalse(old.exists())
        self.assertTrue(recent.is_dir())
        self.assertTrue(newest.is_dir())
        self.assertTrue(all(path.is_dir() for path in protected))
        self.assertTrue(linked.is_symlink())
        self.assertTrue(outside.is_dir())

    def test_invalid_keep_does_not_create_a_build_snapshot(self):
        with patch.object(build, "EXTERNAL_DRIVE", self.root), \
                patch.object(Path, "is_mount", return_value=True):
            with self.assertRaisesRegex(ValueError, "at least 1"):
                build.container_build(self.repository, self.workspace, "unused", "test", [], keep=0)
        self.assertEqual(list(self.workspace.iterdir()), [])


if __name__ == "__main__":
    unittest.main()
