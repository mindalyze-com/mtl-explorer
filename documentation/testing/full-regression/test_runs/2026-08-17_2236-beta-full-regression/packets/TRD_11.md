# Packet: TRD_11

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TRD_11
- In scope: Temporary rider-weight energy what-if recalculation without saving.
- Out of scope: Permanently changing the rider profile.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_10.
- Required app/data state: Track 100017 restored to Running with 75 kg model mass.
- Required browser context: Track Details Overview energy section.

## Allowed Mutations

- Allowed: Change the dialog-only rider-weight control and close without saving.
- Not allowed: Select Save or persist rider-weight changes.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| TRD_11 | Changed the rider-weight what-if control from 75 to 100 kg, inspected results, closed without Save, and directly reloaded the route. | Displayed energy updates immediately, while the stored/default model remains unchanged. | Dialog Total changed 3.1→4.1 Wh, Delta showed +1.0 Wh, and Avg power changed 93→123 W. Closing and reloading returned/remained at 3.1 Wh and 93 W. | PASS | [assets/TRD_11-weight-what-if.txt](../assets/TRD_11-weight-what-if.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_11-weight-what-if.txt](../assets/TRD_11-weight-what-if.txt) | Before/after dialog values and close/reload non-persistence proof. |

## Screenshot Evidence

Unavailable under ACC_04. Exact rendered dialog and Overview values provide direct UI evidence.

## Timings

| Step | Timing |
|---|---:|
| What-if input and recalculation | Under 1 s |
| Close and reload persistence check | About 2 s |

## Handoff Notes

- Completed: Temporary recalculation and non-persistence verification.
- Remaining unfinished coverage: None for TRD_11.
- Blocked or not applicable: None.
- State left for the next packet: Track 100017 Overview open at the unchanged 75 kg baseline.
