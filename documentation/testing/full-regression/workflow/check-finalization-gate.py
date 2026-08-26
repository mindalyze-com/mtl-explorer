#!/usr/bin/env python3
"""Validate that a full-regression run is ready for report/cleanup."""

from __future__ import annotations

import re
import sys
from pathlib import Path


OPEN_STATUSES = {"NOT STARTED", "IN PROGRESS", "PARTIAL", "NOT COVERED"}
TERMINAL_STATUSES = {
    "PASS",
    "FAIL",
    "BLOCKED",
    "NOT APPLICABLE",
    "FIXED",
    "REJECTED",
    "NOT REPRODUCEABLE",
    "NOT REPRODUCIBLE",
}
ALL_STATUSES = OPEN_STATUSES | TERMINAL_STATUSES
FINDING_STATUSES = {
    "OPEN",
    "FIX_IN_WORK",
    "FIXED",
    "REJECTED",
    "NOT REPRODUCEABLE",
    "NOT REPRODUCIBLE",
}

PLAN_ID_RE = re.compile(r"\*\*([A-Z]{3}_[0-9]{2})\*\*")
PLAN_SNAPSHOT_NAME = "coverage-plan.md"
RUN_ROW_RE = re.compile(
    r"^\|\s*([A-Z]{3}_[0-9]{2}|RUN_SETUP|RUN_CLEANUP)\s*\|\s*([^|]+?)\s*\|"
)
MARKDOWN_LINK_RE = re.compile(r"^\[[^\]]+\]\(([^)]+)\)$")


def usage() -> int:
    print(
        "Usage: check-finalization-gate.py "
        "documentation/testing/full-regression/test_runs/<run>/run-state.md",
        file=sys.stderr,
    )
    return 2


def normalize_status(value: str) -> str:
    return " ".join(value.strip().upper().split())


def normalize_packet_cell(value: str) -> str:
    value = value.strip()
    match = MARKDOWN_LINK_RE.match(value)
    if match:
        return match.group(1).strip()
    return value


def load_plan_ids(plan_path: Path) -> list[str]:
    plan_text = plan_path.read_text(encoding="utf-8")
    return list(dict.fromkeys(PLAN_ID_RE.findall(plan_text)))


def resolve_plan_ids(
    run_state_path: Path, run_rows: dict[str, tuple[str, str]]
) -> tuple[list[str], str | None]:
    snapshot_path = run_state_path.parent / PLAN_SNAPSHOT_NAME
    if snapshot_path.exists():
        return load_plan_ids(snapshot_path), None

    legacy_ids = [
        coverage_id
        for coverage_id in run_rows
        if not coverage_id.startswith("RUN_")
    ]
    if legacy_ids:
        return legacy_ids, (
            f"legacy run has no {PLAN_SNAPSHOT_NAME}; using its recorded run-state queue"
        )

    plan_path = Path(__file__).resolve().parents[2] / "frontend-regression-test-plan.md"
    return load_plan_ids(plan_path), (
        f"run has no {PLAN_SNAPSHOT_NAME} or recorded coverage rows; using {plan_path}"
    )


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
        rows[coverage_id] = (normalize_status(cells[1]), normalize_packet_cell(cells[3]))
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


def load_finding_statuses(run_state_path: Path) -> list[tuple[str, str]]:
    findings: list[tuple[str, str]] = []
    in_issues = False

    for line in run_state_path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if stripped == "## Issues":
            in_issues = True
            continue
        if in_issues and stripped.startswith("## "):
            break
        if not in_issues or not stripped.startswith("|"):
            continue

        cells = [cell.strip() for cell in stripped.strip("|").split("|")]
        if len(cells) < 5 or cells[0] == "ID" or set(cells[0]) == {"-"}:
            continue
        findings.append((cells[0], normalize_status(cells[-1])))

    return findings


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        return usage()

    run_state_path = Path(argv[1]).resolve()
    if not run_state_path.exists():
        print(f"FAIL: run-state.md not found: {run_state_path}", file=sys.stderr)
        return 2

    run_dir = run_state_path.parent
    run_rows = load_run_rows(run_state_path)
    plan_ids, plan_warning = resolve_plan_ids(run_state_path, run_rows)

    errors: list[str] = []
    warnings: list[str] = []
    if plan_warning:
        warnings.append(plan_warning)

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

    for finding_id, finding_status in load_finding_statuses(run_state_path):
        if finding_status not in FINDING_STATUSES:
            errors.append(
                f"{finding_id}: unknown finding status {finding_status!r}; "
                f"expected one of {', '.join(sorted(FINDING_STATUSES))}"
            )

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
