# Packet: ACC_05

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: ACC_05
- In scope: Record constraints that may affect later coverage outcomes.
- Out of scope: Deciding final statuses for later rows before they are executed.

## Prerequisites

- Required previous coverage IDs or run packets: RUN_SETUP, ACC_01 through ACC_04.
- Required app/data state: quick-install stack running.
- Required browser context: none.

## Allowed Mutations

- Allowed: update ACC_05 packet, run-state, and constraint evidence.
- Not allowed: silently skip constrained checks.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ACC_05 | Recorded concrete run constraints that affect how later rows should be handled. | Time, tooling, viewport, data, permission, or environment constraints are explicit rather than silently collapsed into parent rows. | Constraints are documented for remote plain-HTTP GPS behavior, installed-PWA-only offline semantics, Docker prerequisite setup, SSH password rotation, and standalone Playwright fallback after in-app browser plugin initialization failure. | PASS | [assets/ACC_05-constraints.txt](../assets/ACC_05-constraints.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/ACC_05-constraints.txt](../assets/ACC_05-constraints.txt) | Explicit constraints to apply to later packet decisions. |

## Screenshot Evidence

Not applicable; this is a workflow accounting check.

## Timings

| Step | Timing |
|---|---:|
| Constraint audit | <1 minute |

## Handoff Notes

- Completed: ACC_05 is terminal.
- Remaining unfinished coverage: DAT_01 onward.
- Blocked or not applicable: none for ACC_05; later GPS and PWA rows must apply the recorded constraints.
- State left for the next packet: coverage accounting setup is complete; next packet begins public test data staging.
