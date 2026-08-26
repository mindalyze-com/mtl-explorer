# Packet: TRD_10

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TRD_10
- In scope: Save an activity-type change and verify automatic energy recalculation.
- Out of scope: Temporary rider-weight what-if calculation.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_01 and TRD_09.
- Required app/data state: Track 100005 starts as Walking with 346.7 Wh.
- Required browser context: Authenticated Quality and Overview tabs.

## Allowed Mutations

- Allowed: Change Walking to Bicycle, verify persistence, then restore Walking.
- Not allowed: Leave the shared track mutated after the packet.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_10 | Save Bicycle for track 100005, reload, inspect energy, then restore Walking. | Activity saves and energy values update automatically. | Bicycle saved and survived reload; net energy changed 346.7 -> 395.1 Wh. Restoring Walking saved and returned energy to 346.7 Wh. | PASS | [assets/TRD_10-activity-energy.txt](../assets/TRD_10-activity-energy.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_10-activity-energy.txt](../assets/TRD_10-activity-energy.txt) | Saved state, recalculated values, reload, and restoration. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible saved values and success state are linked above.

## Timings

| Step | Timing |
|---|---:|
| Change, reload, and restore | 4 min |

## Handoff Notes

- Completed: Saved activity mutation, automatic energy update, persistence, and restoration.
- Remaining unfinished coverage: None for TRD_10.
- Blocked or not applicable: None.
- State left for the next packet: Track 100005 restored to Walking and 346.7 Wh.
