# Packet: TBS_11

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TBS_11
- In scope: Highlight ranking drill-down, selected-track opening, conditional excluded-highlight count, excluded subset, and restoration.
- Out of scope: Persistent curation changes; test exclusion must be restored.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_10.
- Required app/data state: Stable 13-track set with no existing highlight exclusions.
- Required browser context: Statistics Overview, Tracks, and Track Details Quality.

## Allowed Mutations

- Allowed: Temporarily exclude one ranking row from highlights and restore it to Included.
- Not allowed: Change statistics inclusion or leave curation modified.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| TBS_11 | Opened the 13-row Longest track list and rank-1 details; excluded rank 1, opened the 1-track Excluded subset, then restored Highlights to Included and reloaded. | Highlight drill-down lists/open work; excluded-highlight counts appear when applicable. | Ranking exposed all 13 rows and #100002 details. Temporary exclusion showed "1 track excluded", a correct single-row preset, and recomputed the top result. Restoration removed the count and restored Moselradweg as longest. | PASS | [assets/TBS_11-highlight-drilldown.txt](../assets/TBS_11-highlight-drilldown.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_11-highlight-drilldown.txt](../assets/TBS_11-highlight-drilldown.txt) | Ranking, detail identity, conditional count, preset row, and complete restoration evidence. |

## Screenshot Evidence

Unavailable under ACC_04. Exact ranking count/rows, route/ID, count text, badge/reason, curation controls, and restored Overview provide direct DOM evidence.

## Timings

| Step | Timing |
|---|---:|
| Ranking and selected-track navigation | About 8 s |
| Temporary exclusion and count/preset check | About 7 s |
| Quality restoration and freshness reload | About 9 s |

## Handoff Notes

- Completed: Highlight ranking list/open, exclusion count/subset, and restoration.
- Remaining unfinished coverage: None for TBS_11.
- Blocked or not applicable: None.
- State left for the next packet: Overview is open with 13 tracks; Moselradweg curation is fully Included; no highlight-excluded count remains.
