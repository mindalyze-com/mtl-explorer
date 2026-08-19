# Packet: TRD_12

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TRD_12
- In scope: Exclude one track from statistics, verify overview removal, then re-include it.
- Out of scope: Highlight-only exclusion.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_10.
- Required app/data state: Fifteen included tracks; controlled latest track 100017.
- Required browser context: Track Details Quality and Statistics Overview.

## Allowed Mutations

- Allowed: Temporarily set the controlled track's statistics exclusion reason, then restore Included.
- Not allowed: Leave the controlled track excluded.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| TRD_12 | Changed Statistics from Included to Other, opened Statistics Overview, then restored Included and reopened Statistics Overview. | Excluded track stops counting; re-inclusion restores it. | Count changed 15→14 and the latest-date boundary changed 18→17 August. Re-inclusion restored count 15, the controlled recent-activity row, and the 18 August boundary. | PASS | [assets/TRD_12-statistics-exclusion.txt](../assets/TRD_12-statistics-exclusion.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_12-statistics-exclusion.txt](../assets/TRD_12-statistics-exclusion.txt) | Baseline, excluded, and restored rendered statistics values and record visibility. |

## Screenshot Evidence

Unavailable under ACC_04. Exact rendered counts, date boundaries, and Recent Activity membership provide direct UI evidence.

## Timings

| Step | Timing |
|---|---:|
| Exclude and verify overview | About 5 s |
| Re-include and verify overview | About 5 s |

## Handoff Notes

- Completed: Exclusion effect and re-inclusion restoration.
- Remaining unfinished coverage: None for TRD_12.
- Blocked or not applicable: None.
- State left for the next packet: Statistics Overview open; track 100017 is Included.
