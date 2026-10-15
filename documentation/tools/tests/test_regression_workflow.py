# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2020-2026 Patrick Heusser and MTL Explorer contributors.
"""Regression workflow path and gate checks using synthetic run folders."""

import importlib.util
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

from documentation.tools.repository_paths import private_repository

REPOSITORY = Path(__file__).resolve().parents[3]
TOOLS = REPOSITORY / "documentation/tools/testing/full-regression"


class RegressionWorkflowTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="mtl-regression-tool-test-")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)

    def test_initializer_freezes_current_plan_and_gate_rejects_open_queue(self):
        subprocess.run([
            sys.executable, str(TOOLS / "init-run.py"),
            "--server", "example.invalid", "--ssh-user", "synthetic",
            "--app-image", "example.invalid/mtl:synthetic", "--run-id", "documentation-smoke",
            "--output-root", str(self.root),
        ], check=True, capture_output=True, text=True)
        run = self.root / "documentation-smoke"
        self.assertEqual((run / "coverage-plan.md").read_bytes(),
                         (REPOSITORY / "documentation/development/testing/full-regression/coverage-plan.md").read_bytes())
        state = (run / "run-state.md").read_text()
        self.assertIn("documentation/development/testing/full-regression/coverage-plan.md", state)
        rejected = subprocess.run([sys.executable, str(TOOLS / "check-finalization-gate.py"),
                                   str(run / "run-state.md")], capture_output=True, text=True)
        self.assertEqual(rejected.returncode, 1)
        self.assertIn("still resumable", rejected.stdout)

    def test_gate_accepts_terminal_frozen_queue_and_rejects_unknown_finding_status(self):
        (self.root / "packets").mkdir()
        (self.root / "coverage-plan.md").write_text("# Synthetic coverage\n\n**DOC_01** Read a guide.\n")
        state = self.root / "run-state.md"
        state.write_text("# Synthetic tool check\n\n| DOC_01 | PASS | | packets/DOC_01.md | |\n")
        (self.root / "packets/DOC_01.md").write_text("# Synthetic packet\n\n| DOC_01 | Action | Expected | Observed | PASS | Evidence |\n")
        result = subprocess.run([sys.executable, str(TOOLS / "check-finalization-gate.py"), str(state)],
                                capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertIn("1 coverage IDs terminal", result.stdout)
        state.write_text(state.read_text() + "\n## Issues\n\n| DOC-I001 | Summary | Evidence | Action | UNKNOWN |\n")
        rejected = subprocess.run([sys.executable, str(TOOLS / "check-finalization-gate.py"), str(state)],
                                  capture_output=True, text=True)
        self.assertEqual(rejected.returncode, 1)
        self.assertIn("unknown finding status", rejected.stdout)

    def test_default_run_location_is_private_verification(self):
        spec = importlib.util.spec_from_file_location("regression_initializer", TOOLS / "init-run.py")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        self.assertEqual(module.DEFAULT_RUNS_ROOT, private_repository(REPOSITORY) / "docs-my/verification/regression/full-suite")
        self.assertTrue(module.PLAN_SOURCE_PATH.is_file())

    def test_private_location_override_and_nested_checkout_rejection(self):
        with patch.dict(os.environ, {"MTL_PRIVATE_REPO": str(self.root)}):
            self.assertEqual(private_repository(REPOSITORY), self.root.resolve())
        with patch.dict(os.environ, {"MTL_PRIVATE_REPO": str(REPOSITORY / "private")}):
            with self.assertRaisesRegex(ValueError, "separate checkout"):
                private_repository(REPOSITORY)

    def test_missing_private_checkout_requires_an_explicit_output_location(self):
        missing = self.root / "missing-private"
        result = subprocess.run([
            sys.executable, str(TOOLS / "init-run.py"), "--server", "example.invalid",
            "--app-image", "example.invalid/mtl:synthetic", "--run-id", "missing-private",
        ], env={**os.environ, "MTL_PRIVATE_REPO": str(missing)}, capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("supply --output-root", result.stderr)
        self.assertFalse(missing.exists())


if __name__ == "__main__":
    unittest.main()
