#!/usr/bin/env python3
"""Validate that a full-regression run is ready for report/cleanup."""

from __future__ import annotations

import re
import sys
from pathlib import Path


OPEN_STATUSES = {"NOT STARTED", "IN PROGRESS", "PARTIAL", "NOT COVERED"}
TERMINAL_STATUSES = {"PASS", "FAIL", "BLOCKED", "NOT APPLICABLE"}
ALL_STATUSES = OPEN_STATUSES | TERMINAL_STATUSES

PLAN_ID_RE = re.compile(r"\*\*([A-Z]{3}_[0-9]{2})\*\*")
RUN_ROW_RE = re.compile(
    r"^\|\s*([A-Z]{3}_[0-9]{2}|RUN_SETUP|RUN_CLEANUP)\s*\|\s*([^|]+?)\s*\|"
)


def usage() -> int:
    print(
        "Usage: check-finalization-gate.py "
        "documentation/testing/full-regression/test_runs/<run>/run-state.md",
        file=sys.stderr,
    )
    return 2


def normalize_status(value: str) -> str:
    return " ".join(value.strip().upper().split())


def load_plan_ids(script_path: Path) -> list[str]:
    plan_path = script_path.parents[2] / "frontend-regression-test-plan.md"
    plan_text = plan_path.read_text(encoding="utf-8")
    return list(dict.fromkeys(PLAN_ID_RE.findall(plan_text)))


def load_run_rows(run_state_path: Path) -> dict[str, tuple[str, str]]:
    rows: dict[str, tuple[str, str]] = {}
    for line in run_state_path.read_text(encoding="utf-8").splitlines():
        match = RUN_ROW_RE.match(line)
        if not match:
            continue
        coverage_id = match.group(1)
        cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
        if len(cells) < 4 or cells[0] == "Coverage ID":
            continue
        rows[coverage_id] = (normalize_status(cells[1]), cells[3])
    return rows


def load_packet_status(packet_path: Path, coverage_id: str) -> str | None:
    if not packet_path.exists():
        return None

    for line in packet_path.read_text(encoding="utf-8").splitlines():
        cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
        if len(cells) < 6 or cells[0] != coverage_id:
            continue
        return normalize_status(cells[4])
    return None


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        return usage()

    run_state_path = Path(argv[1]).resolve()
    if not run_state_path.exists():
        print(f"FAIL: run-state.md not found: {run_state_path}", file=sys.stderr)
        return 2

    run_dir = run_state_path.parent
    plan_ids = load_plan_ids(Path(__file__).resolve())
    run_rows = load_run_rows(run_state_path)

    errors: list[str] = []
    warnings: list[str] = []

    for coverage_id in plan_ids:
        row = run_rows.get(coverage_id)
        if row is None:
            errors.append(f"{coverage_id}: missing from run-state queue")
            continue

        run_status, packet_cell = row
        if run_status not in ALL_STATUSES:
            errors.append(f"{coverage_id}: unknown run-state status {run_status!r}")
        elif run_status in OPEN_STATUSES:
            errors.append(f"{coverage_id}: run-state status is still resumable ({run_status})")

        packet_rel = packet_cell or f"packets/{coverage_id}.md"
        packet_path = run_dir / packet_rel
        packet_status = load_packet_status(packet_path, coverage_id)
        if packet_status is None:
            errors.append(f"{coverage_id}: missing packet status in {packet_rel}")
            continue

        if packet_status not in ALL_STATUSES:
            errors.append(f"{coverage_id}: unknown packet status {packet_status!r}")
        elif packet_status in OPEN_STATUSES:
            errors.append(f"{coverage_id}: packet status is still resumable ({packet_status})")

        if (
            run_status in ALL_STATUSES
            and packet_status in ALL_STATUSES
            and run_status != packet_status
        ):
            warnings.append(
                f"{coverage_id}: run-state status {run_status} differs from packet status {packet_status}"
            )

    extra_open = []
    for coverage_id, (run_status, _) in run_rows.items():
        if coverage_id.startswith("RUN_"):
            continue
        if coverage_id not in plan_ids and run_status in OPEN_STATUSES:
            extra_open.append(f"{coverage_id}: extra open run-state row ({run_status})")
    errors.extend(extra_open)

    if warnings:
        print("Warnings:")
        for warning in warnings:
            print(f"- {warning}")

    if errors:
        print("Finalization gate: FAIL")
        for error in errors:
            print(f"- {error}")
        print(
            "\nOpen statuses are resumable and must be continued: "
            f"{', '.join(sorted(OPEN_STATUSES))}"
        )
        return 1

    print(f"Finalization gate: PASS ({len(plan_ids)} coverage IDs terminal)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
