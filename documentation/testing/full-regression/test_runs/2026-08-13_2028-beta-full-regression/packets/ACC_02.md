# Packet: ACC_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: ACC_02.
- In scope: verify parent sections and ID prefixes cannot substitute for direct child coverage.
- Out of scope: executing later product checks.

## Prerequisites

- Required previous coverage IDs or run packets: RUN_SETUP, ACC_01.
- Required app/data state: exact 193-ID frozen queue.
- Required browser context: none.

## Allowed Mutations

- Allowed: save this packet and advance run state.
- Not allowed: mark a chapter/prefix PASS or remove child IDs.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ACC_02 | Audited the frozen queue and packet naming/advancement rules for parent-section shortcuts. | No section may pass while any child is skipped, spot-checked, or only indirectly verified. | The queue contains only individual coverage-ID rows plus `RUN_SETUP`/`RUN_CLEANUP`; there are no chapter or prefix substitute rows. Each coverage ID requires its own packet, and unfinished statuses remain resumable. | PASS | [assets/ACC_02-accounting-policy.txt](../assets/ACC_02-accounting-policy.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/ACC_02-accounting-policy.txt](../assets/ACC_02-accounting-policy.txt) | Individual-row and no-parent-shortcut audit. |

## Screenshot Evidence

Not applicable; this is a workflow accounting check.

## Timings

| Step | Timing |
|---|---:|
| Accounting-policy audit | < 1 min |

## Handoff Notes

- Completed: ACC_02 is terminal.
- Remaining unfinished coverage: ACC_03 onward.
- Blocked or not applicable: none.
- State left for the next packet: no parent/prefix shortcut rows exist.
