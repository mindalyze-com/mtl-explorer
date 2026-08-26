# Packet: TRD_11

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TRD_11
- In scope: Unsaved rider-weight energy recalculation.
- Out of scope: Persistent activity-type recalculation.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_10.
- Required app/data state: Track 100005 restored to Walking, rider 75 kg, 346.7 Wh.
- Required browser context: Authenticated Overview and Adjust rider weight dialog.

## Allowed Mutations

- Allowed: Change dialog weight to 90 kg and close without Save.
- Not allowed: Persist the what-if value.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_11 | Change rider weight 75 -> 90 kg, inspect recalculation, close without Save, and reopen. | Displayed energy changes without permanent persistence. | Total changed 346.7 -> 416.0 Wh, delta +69.3 Wh, and power 702 -> 842 W. Reopen restored 75 kg and 346.7 Wh. | PASS | [assets/TRD_11-energy-what-if.txt](../assets/TRD_11-energy-what-if.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_11-energy-what-if.txt](../assets/TRD_11-energy-what-if.txt) | Baseline, recalculation, and no-save verification. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible dialog values are linked above.

## Timings

| Step | Timing |
|---|---:|
| What-if change and reopen verification | 2 min |

## Handoff Notes

- Completed: Temporary weight recalculation and non-persistence.
- Remaining unfinished coverage: None for TRD_11.
- Blocked or not applicable: None.
- State left for the next packet: Baseline rider and track energy retained; dialog open at 75 kg.
