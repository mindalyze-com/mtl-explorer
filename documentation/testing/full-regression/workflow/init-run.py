#!/usr/bin/env python3
"""Create a resumable full-regression run with a frozen coverage queue."""

from __future__ import annotations

import argparse
import hashlib
import re
import shutil
import subprocess
import tempfile
from collections import Counter
from datetime import datetime
from pathlib import Path


PLAN_ID_RE = re.compile(r"\*\*([A-Z]{3}_[0-9]{2})\*\*")
SAFE_RUN_ID_RE = re.compile(r"^[A-Za-z0-9._-]+$")
SLUG_SEPARATOR_RE = re.compile(r"[^a-z0-9]+")
DEFAULT_SLUG = "beta-full-regression"
PLAN_SNAPSHOT_NAME = "coverage-plan.md"
PLAN_SOURCE_REL = Path("documentation/testing/frontend-regression-test-plan.md")

SCRIPT_PATH = Path(__file__).resolve()
REPOSITORY_ROOT = SCRIPT_PATH.parents[4]
FULL_REGRESSION_DIR = SCRIPT_PATH.parents[1]
DEFAULT_RUNS_ROOT = FULL_REGRESSION_DIR / "test_runs"
PLAN_SOURCE_PATH = REPOSITORY_ROOT / PLAN_SOURCE_REL


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Create a run folder, freeze the current coverage plan, and initialize "
            "the complete run-state queue."
        )
    )
    parser.add_argument("--server", required=True, help="Target server IP or host")
    parser.add_argument("--ssh-user", default="root", help="Target SSH user")
    parser.add_argument("--app-image", required=True, help="App container image")
    parser.add_argument(
        "--slug",
        default=DEFAULT_SLUG,
        help=f"Run id suffix (default: {DEFAULT_SLUG})",
    )
    parser.add_argument(
        "--run-id",
        help="Exact run id for controlled runs; otherwise generated from local time",
    )
    parser.add_argument(
        "--output-root",
        type=Path,
        default=DEFAULT_RUNS_ROOT,
        help="Run-folder parent (defaults to the repository test_runs folder)",
    )
    parser.add_argument("--coordinator", default="Codex", help="Coordinator label")
    return parser.parse_args()


def normalize_slug(value: str) -> str:
    slug = SLUG_SEPARATOR_RE.sub("-", value.strip().lower()).strip("-")
    if not slug:
        raise ValueError("slug must contain at least one letter or number")
    return slug


def create_run_id(now: datetime, slug: str, requested: str | None) -> str:
    if requested:
        if not SAFE_RUN_ID_RE.fullmatch(requested):
            raise ValueError(
                "run id may contain only letters, numbers, dot, underscore, and hyphen"
            )
        return requested
    return f"{now:%Y-%m-%d_%H%M}-{slug}"


def load_plan() -> tuple[bytes, list[str]]:
    plan_bytes = PLAN_SOURCE_PATH.read_bytes()
    plan_text = plan_bytes.decode("utf-8")
    all_ids = PLAN_ID_RE.findall(plan_text)
    duplicates = sorted(
        coverage_id for coverage_id, count in Counter(all_ids).items() if count > 1
    )
    if duplicates:
        raise ValueError(f"duplicate coverage IDs in plan: {', '.join(duplicates)}")
    if not all_ids:
        raise ValueError(f"no coverage IDs found in {PLAN_SOURCE_REL}")
    return plan_bytes, all_ids


def git_revision() -> str:
    result = subprocess.run(
        ["git", "-C", str(REPOSITORY_ROOT), "rev-parse", "HEAD"],
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        return "UNKNOWN"
    return result.stdout.strip() or "UNKNOWN"


def single_line(value: str) -> str:
    return " ".join(value.split()).replace("|", "\\|")


def run_state_text(
    *,
    run_id: str,
    server: str,
    ssh_user: str,
    app_image: str,
    coordinator: str,
    started: datetime,
    plan_sha256: str,
    plan_revision: str,
    coverage_ids: list[str],
) -> str:
    first_coverage_id = coverage_ids[0]
    queue_rows = [
        "| RUN_SETUP | NOT STARTED | | packets/RUN_SETUP.md | |",
        *(
            f"| {coverage_id} | NOT STARTED | | packets/{coverage_id}.md | |"
            for coverage_id in coverage_ids
        ),
        "| RUN_CLEANUP | NOT STARTED | | packets/RUN_CLEANUP.md | |",
    ]

    return "\n".join(
        [
            "# Full Regression Run State",
            "",
            "## Run",
            "",
            "| Field | Value |",
            "|---|---|",
            f"| Run id | {run_id} |",
            f"| Target server | {single_line(server)} |",
            f"| SSH user | {single_line(ssh_user)} |",
            (
                "| Source | GitHub main quick install, with app image override "
                f"`{single_line(app_image)}` |"
            ),
            f"| App image | `{single_line(app_image)}` |",
            "| Coverage plan snapshot | `coverage-plan.md` |",
            f"| Coverage plan source | `{PLAN_SOURCE_REL.as_posix()}` |",
            f"| Coverage plan Git revision | `{plan_revision}` |",
            f"| Coverage plan SHA-256 | `{plan_sha256}` |",
            "| App URL | |",
            f"| Started | {started.isoformat(timespec='seconds')} |",
            f"| Coordinator | {single_line(coordinator)} |",
            "",
            "## Shared Facts",
            "",
            "- README facts:",
            "- Login credentials source:",
            "- Import folder:",
            "- Browser contexts:",
            "- Known constraints:",
            "",
            "## Queue",
            "",
            "- Source queue: `coverage-plan.md`",
            "- Current coverage ID: RUN_SETUP",
            f"- Next coverage ID: {first_coverage_id}",
            f"- Frozen coverage ID count: {len(coverage_ids)}",
            "",
            "Track active, blocked, failed, and recently completed IDs here. Completed packet",
            "files are the durable record.",
            "",
            "| Coverage ID | Status | Owner | Packet file | Notes |",
            "|---|---|---|---|---|",
            *queue_rows,
            "",
            "## Issues",
            "",
            (
                "Finding statuses: `OPEN`, `FIX_IN_WORK`, `FIXED`, `REJECTED`, "
                "`NOT REPRODUCIBLE` (`NOT REPRODUCEABLE` is accepted for legacy runs)."
            ),
            "",
            "| ID | Severity | Coverage ID | Summary | Status |",
            "|---|---|---|---|---|",
            "",
            "## Final Assembly Notes",
            "",
            "- Missing coverage IDs:",
            "- Cleanup state:",
            "- Final report path:",
            "- Finalization gate:",
            "- Early closure approval:",
            "",
        ]
    )


def display_path(path: Path) -> str:
    try:
        return path.relative_to(REPOSITORY_ROOT).as_posix()
    except ValueError:
        return str(path)


def main() -> int:
    args = parse_args()
    now = datetime.now().astimezone()

    try:
        slug = normalize_slug(args.slug)
        run_id = create_run_id(now, slug, args.run_id)
        plan_bytes, coverage_ids = load_plan()
    except (OSError, UnicodeError, ValueError) as error:
        raise SystemExit(f"FAIL: {error}") from error

    output_root = args.output_root.expanduser().resolve()
    run_dir = output_root / run_id
    if run_dir.exists():
        raise SystemExit(f"FAIL: run folder already exists: {run_dir}")

    plan_sha256 = hashlib.sha256(plan_bytes).hexdigest()
    plan_revision = git_revision()
    state_text = run_state_text(
        run_id=run_id,
        server=args.server,
        ssh_user=args.ssh_user,
        app_image=args.app_image,
        coordinator=args.coordinator,
        started=now,
        plan_sha256=plan_sha256,
        plan_revision=plan_revision,
        coverage_ids=coverage_ids,
    )

    output_root.mkdir(parents=True, exist_ok=True)
    temporary_dir = Path(tempfile.mkdtemp(prefix=f".{run_id}-", dir=output_root))
    try:
        (temporary_dir / "packets").mkdir()
        (temporary_dir / "assets").mkdir()
        (temporary_dir / PLAN_SNAPSHOT_NAME).write_bytes(plan_bytes)
        (temporary_dir / "run-state.md").write_text(state_text, encoding="utf-8")
        temporary_dir.rename(run_dir)
    except Exception:
        shutil.rmtree(temporary_dir, ignore_errors=True)
        raise

    print(f"Run folder: {display_path(run_dir)}")
    print(f"Run state: {display_path(run_dir / 'run-state.md')}")
    print(f"Coverage plan: {display_path(run_dir / PLAN_SNAPSHOT_NAME)}")
    print(f"Coverage IDs: {len(coverage_ids)}")
    print(f"Coverage plan SHA-256: {plan_sha256}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
