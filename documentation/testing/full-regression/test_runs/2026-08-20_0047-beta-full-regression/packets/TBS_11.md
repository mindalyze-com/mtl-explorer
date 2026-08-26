# Packet: TBS_11

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TBS_11
- In scope: Highlight drilldown list, selected-track navigation, and excluded-highlight counts.
- Out of scope: Geo-filter Statistics parity, covered by TBS_12.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_10.
- Required app/data state: Mosel is the current longest track.
- Required browser context: Statistics Overview, Stats Tracks, and Track Quality.

## Allowed Mutations

- Allowed: Temporarily exclude Mosel from highlights with a reason and restore it.
- Not allowed: Leave highlight curation changed.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TBS_11 | Open Longest drilldown, select track, exclude it from highlights, inspect count/list, then restore. | Expected list/details open and excluded count is exposed. | One-row drilldown/details passed; 1 track excluded opened the marked Mosel list; restore removed count and restored ranking. | PASS | [assets/TBS_11-highlight-drilldown-curation.txt](../assets/TBS_11-highlight-drilldown-curation.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_11-highlight-drilldown-curation.txt](../assets/TBS_11-highlight-drilldown-curation.txt) | Drilldown, exclusion count/list, and restored state. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible labels and curation states are linked above.

## Timings

| Step | Timing |
|---|---:|
| Drilldown and track navigation | 2 min |
| Exclude, inspect, and restore | 5 min |

## Handoff Notes

- Completed: Highlight drilldown and excluded-count flow.
- Remaining unfinished coverage: None for TBS_11.
- Blocked or not applicable: None.
- State left for the next packet: Statistics Overview, seven tracks, Mosel included/restored.
