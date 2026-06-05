# Packet: TRD_12

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: TRD_12
- In scope: Verify exclude from statistics removes a track from stats overview and re-including restores it.
- Out of scope: Other coverage IDs except where noted as shared state/evidence.

## Prerequisites

- Required previous coverage IDs or run packets: Previous queue rows terminal or explicitly not required.
- Required app/data state: Current run-state and packet set for this run.
- Required browser context: As specified by the coverage action.

## Allowed Mutations

- Allowed: Read-only verification and packet/run-state updates.
- Not allowed: Collapse this ID into a parent row or skip required direct evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_12 | Set track 100005 Statistics control to Exclude: GPS noise, checked API totals and Stats overview, then restored Included in statistics and checked totals again. | Excluded track stops counting in stats overview; re-including brings it back. | Included count changed from 11 to 10 and Stats showed 10 tracks after exclusion; re-including restored 11 tracks, distance, and energy totals. | PASS | [assets/TRD_12-after-exclude-gps-noise.webp](../assets/TRD_12-after-exclude-gps-noise.webp); [assets/TRD_12-stats-after-exclusion.webp](../assets/TRD_12-stats-after-exclusion.webp); [assets/TRD_12-after-reinclude.webp](../assets/TRD_12-after-reinclude.webp); [assets/TRD_12-stats-after-reinclude.webp](../assets/TRD_12-stats-after-reinclude.webp); [assets/TRD_12-statistics-exclusion-summary.txt](../assets/TRD_12-statistics-exclusion-summary.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_12-after-exclude-gps-noise.webp](../assets/TRD_12-after-exclude-gps-noise.webp) | Screenshot evidence |
| [assets/TRD_12-stats-after-exclusion.webp](../assets/TRD_12-stats-after-exclusion.webp) | Screenshot evidence |
| [assets/TRD_12-after-reinclude.webp](../assets/TRD_12-after-reinclude.webp) | Screenshot evidence |
| [assets/TRD_12-stats-after-reinclude.webp](../assets/TRD_12-stats-after-reinclude.webp) | Screenshot evidence |
| [assets/TRD_12-statistics-exclusion-summary.txt](../assets/TRD_12-statistics-exclusion-summary.txt) | Text/log evidence |

## Screenshot Evidence

![assets/TRD_12-after-exclude-gps-noise.webp](../assets/TRD_12-after-exclude-gps-noise.webp)
![assets/TRD_12-stats-after-exclusion.webp](../assets/TRD_12-stats-after-exclusion.webp)
![assets/TRD_12-after-reinclude.webp](../assets/TRD_12-after-reinclude.webp)
![assets/TRD_12-stats-after-reinclude.webp](../assets/TRD_12-stats-after-reinclude.webp)

## Timings

| Step | Timing |
|---|---:|
| Packet execution | <1 minute |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
