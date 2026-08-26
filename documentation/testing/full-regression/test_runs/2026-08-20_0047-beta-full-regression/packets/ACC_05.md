# Packet: ACC_05

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: ACC_05
- In scope: Explicit recording of time, tooling, viewport, data, permission, and environment constraints.
- Out of scope: Converting unreached executable checks into constraints.

## Prerequisites

- Required previous coverage IDs or run packets: ACC_04.
- Required app/data state: RUN_SETUP terminal.
- Required browser context: Connected in-app browser.

## Allowed Mutations

- Allowed: Record concrete constraints and unblock paths.
- Not allowed: Use a constraint as a blanket reason to skip unrelated executable checks.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ACC_05 | Audit current prerequisite, origin, browser, data, permission, and privacy constraints and attach each to the exact affected packet. | Constraints are explicit rather than silently collapsed into broad PASS results. | Docker setup, plain-HTTP geolocation, screenshot capture, empty initial data, and private-data prohibition are explicit with downstream handling. | PASS | [assets/ACC_05-constraints.txt](../assets/ACC_05-constraints.txt); [run-state.md](../run-state.md) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/ACC_05-constraints.txt](../assets/ACC_05-constraints.txt) | Concrete constraints and planned terminal handling. |

## Screenshot Evidence

Not useful for this constraint-accounting check.

## Timings

| Step | Timing |
|---|---:|
| Constraint audit | <1 min |

## Handoff Notes

- Completed: All known constraints are explicit and scoped.
- Remaining unfinished coverage: None for ACC_05.
- Blocked or not applicable: Screenshot capture only; geolocation handling will follow the frozen GPS rows.
- State left for the next packet: Empty dataset; next ID DAT_01 begins public fixture preparation.
