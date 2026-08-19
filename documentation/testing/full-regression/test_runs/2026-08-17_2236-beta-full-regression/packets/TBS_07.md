# Packet: TBS_07

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TBS_07
- In scope: Statistics correctness for many tracks, exactly one track, and no tracks.
- Out of scope: Import/delete mutations covered by TBS_08.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_06.
- Required app/data state: 15-track baseline with a unique 2013 track.
- Required browser context: Filter and Statistics Overview on desktop.

## Allowed Mutations

- Allowed: Select one/no year categories and reset the filter.
- Not allowed: Modify or delete track records.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| TBS_07 | Recorded many-track totals, selected only 2013 for one track, cleared all years for zero tracks, then reset and rechecked many-track totals. | Statistics are correct and stale-free for many, one, and zero tracks. | 15-track, 1-track, and empty Overview states matched map/filter counts and exact expected totals. Empty state removed stale cards. Reset restored the original 15-track totals. | PASS | [assets/TBS_07-cardinality.txt](../assets/TBS_07-cardinality.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_07-cardinality.txt](../assets/TBS_07-cardinality.txt) | Map/filter and Overview values for many, one, zero, and restored states. |

## Screenshot Evidence

Unavailable under ACC_04. Exact accessible counts, totals, card identities, and empty-state text provide direct evidence.

## Timings

| Step | Timing |
|---|---:|
| Configure and verify one-track state | About 12 s |
| Configure and verify empty state | About 11 s |
| Reset and verify many-track state | About 9 s |

## Handoff Notes

- Completed: Many/single/empty statistics and restored-baseline verification.
- Remaining unfinished coverage: None for TBS_07.
- Blocked or not applicable: None.
- State left for the next packet: Filter is reset and Statistics Overview is open with all 15 tracks.
