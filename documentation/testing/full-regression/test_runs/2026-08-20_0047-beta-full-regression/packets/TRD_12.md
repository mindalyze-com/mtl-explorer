# Packet: TRD_12

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TRD_12
- In scope: Exclude one track from statistics and re-include it.
- Out of scope: Highlight-only exclusions.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_10 and IMP_09.
- Required app/data state: Nine-track Statistics baseline; track 100005 included.
- Required browser context: Authenticated Quality and Statistics Overview.

## Allowed Mutations

- Allowed: Set statistics reason to Wrong activity, verify, then restore Included.
- Not allowed: Leave the shared track excluded.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_12 | Exclude track 100005, inspect Stats totals, re-include it, and inspect totals again. | Exclusion removes the track from Stats; re-inclusion brings it back. | Stats changed 9 -> 8 tracks with lower totals and an exclusion indicator, then returned exactly to the nine-track baseline after Included was restored. | PASS | [assets/TRD_12-statistics-exclusion.txt](../assets/TRD_12-statistics-exclusion.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_12-statistics-exclusion.txt](../assets/TRD_12-statistics-exclusion.txt) | Before, excluded, and restored aggregate states. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible aggregate states are linked above.

## Timings

| Step | Timing |
|---|---:|
| Exclude, verify, re-include, verify | 4 min |

## Handoff Notes

- Completed: Statistics exclusion and exact baseline restoration.
- Remaining unfinished coverage: None for TRD_12.
- Blocked or not applicable: None.
- State left for the next packet: Track 100005 included; nine-track Statistics baseline restored.
