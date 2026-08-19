# Packet: MED_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_03
- In scope: Map pin opens photo preview; previous/next works.
- Out of scope: Activity and statistics viewer entry points.

## Prerequisites

- Required previous coverage IDs or run packets: MED_02.
- Required app/data state: Six Bern media points and enabled media layer.
- Required browser context: Bern main map at 100 m and 10 m scales.

## Allowed Mutations

- Allowed: Temporary layer/filter changes for target isolation.
- Not allowed: Media changes.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_03 | Targeted cluster/individual known media coordinates at two zoom levels, with tracks shown, hidden, and filtered to zero. | Pin opens preview and next/previous navigation works. | Reliable WebGL marker targeting was unavailable without screenshots; attempts opened track selection or no sheet, so the pin entry and navigation could not be credited. | BLOCKED | [assets/MED_03-pin-targeting.txt](../assets/MED_03-pin-targeting.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_03-pin-targeting.txt](../assets/MED_03-pin-targeting.txt) | Targeting attempts, isolation steps, constraint, and restoration. |

## Screenshot Evidence

Unavailable under ACC_04; this absence is the blocking condition for precise WebGL marker targeting.

## Timings

| Step | Timing |
|---|---:|
| Cluster/individual targeting attempts | About 20 s |
| Isolation and restoration | About 10 s |

## Handoff Notes

- Completed: Pin-targeting attempt and state restoration.
- Remaining unfinished coverage: None for MED_03; terminal BLOCKED.
- Blocked or not applicable: WebGL marker targeting under ACC_04.
- State left for the next packet: All 13 tracks and media layer enabled.

