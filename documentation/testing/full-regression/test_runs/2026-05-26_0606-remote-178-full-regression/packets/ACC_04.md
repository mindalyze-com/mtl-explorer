# Packet: ACC_04

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: ACC_04
- In scope: Capture representative screenshots.
- Out of scope: Product behavior verification outside coverage accounting.

## Prerequisites

- Required previous coverage IDs or run packets: RUN_SETUP.
- Required app/data state: run-state initialized with full queue.
- Required browser context: not required.

## Allowed Mutations

- Allowed: update this packet and coordinator-owned run-state.
- Not allowed: app/data mutations.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ACC_04 | Reviewed queue/accounting requirement and current run-state/report workflow. | Coverage is tracked per stable ID with explicit evidence and terminal status rules. | Run evidence policy records WebP screenshots for working user-facing surfaces as well as failures; initial login and empty map screenshots already captured. | PASS | assets/RUN_SETUP-login.webp; assets/RUN_SETUP-empty-map.webp |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| run-state.md | Coordinator queue and status source. |

## Timings

| Step | Timing |
|---|---:|
| Accounting verification | <1m |

## Handoff Notes

- Completed: ACC_04 terminal PASS.
- Remaining unfinished coverage: continue queue.
- Blocked or not applicable: none.
- State left for the next packet: no app state changed.
