# Packet: TRD_10

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TRD_10
- In scope: Save a changed activity type and verify automatic energy-value recalculation.
- Out of scope: Custom rider-weight what-if calculations.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_02.
- Required app/data state: Synthetic track 100017 with stable energy values.
- Required browser context: Track Details Quality and Overview tabs.

## Allowed Mutations

- Allowed: Change activity from Running to Bicycle, verify persistence, then restore Running.
- Not allowed: Leave the controlled track mutated.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_10 | Changed track 100017 from Running to Bicycle in Quality, inspected Overview energy, reloaded the route, then restored Running. | Activity saves; dependent energy and power values recalculate automatically and persist. | Bicycle persisted across a direct reload. Net Total changed 3.1→3.8 Wh and Avg Power changed 93→112 W. Restoring Running returned both values to baseline. | PASS | [assets/TRD_10-activity-energy.txt](../assets/TRD_10-activity-energy.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_10-activity-energy.txt](../assets/TRD_10-activity-energy.txt) | Initial, changed, reloaded, and restored activity/energy UI values. |

## Screenshot Evidence

Unavailable under ACC_04. Rendered activity labels and exact before/after energy values provide direct UI evidence.

## Timings

| Step | Timing |
|---|---:|
| Change and automatic recalculation | About 2 s |
| Reload persistence check | About 1 s |
| Restore Running and baseline values | About 2 s |

## Handoff Notes

- Completed: Save, dependent recalculation, persistence, and restoration.
- Remaining unfinished coverage: None for TRD_10.
- Blocked or not applicable: None.
- State left for the next packet: Track 100017 Overview open and restored to Running.
