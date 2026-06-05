# Packet: ACC_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: ACC_01
- In scope: Treat every checklist bullet as required coverage.
- Out of scope: Product behavior not covered by this accounting row.

## Prerequisites

- Required previous coverage IDs or run packets: RUN_SETUP.
- Required app/data state: Run folder and queue initialized.
- Required browser context: Not required unless evidence asset is visual.

## Allowed Mutations

- Allowed: Update run-state and this packet.
- Not allowed: Mutate target app state.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ACC_01 | Audited the resumable workflow artifacts for this accounting requirement. | The run records this requirement directly and keeps unfinished executable coverage visible instead of collapsing it into a parent summary. | Confirmed `run-state.md` contains one row for every 171 coverage IDs parsed from `documentation/testing/frontend-regression-test-plan.md`, plus run setup/cleanup rows. | PASS | run-state queue and [assets/RUN_SETUP-login-map.webp](../assets/RUN_SETUP-login-map.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| run-state.md | Durable coverage queue/status source. |

## Screenshot Evidence

**RUN SETUP login map**

![RUN SETUP login map](../assets/RUN_SETUP-login-map.webp)

## Timings

| Step | Timing |
|---|---:|
| Accounting audit | <1 minute |

## Handoff Notes

- Completed: ACC_01 terminal as `PASS`.
- Remaining unfinished coverage: Continue with next queue ID.
- Blocked or not applicable: None.
- State left for the next packet: No app/data mutation.
