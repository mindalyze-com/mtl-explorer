# Packet: TBS_11

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: TBS_11
- In scope: highlight drilldown list, opening a selected drilldown track, and excluded-highlight count exposure.
- Out of scope: broad track-browser search validation beyond the highlight-count path.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_10.
- Required app/data state: Stats Overview with highlight entries.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: open highlight drilldowns, open a selected track, temporarily exclude one highlight entry to verify count exposure, then restore it.
- Not allowed: leave highlight/statistics exclusion state changed.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TBS_11 | Opened the `Longest track` highlight drilldown, selected the top Moselradweg row, temporarily excluded Moselradweg from highlights, verified excluded-count exposure, and restored the exclusion state. | Highlight drilldowns open the expected track list, a selected track opens, and excluded-highlight counts are exposed where applicable. | The drilldown listed ranked tracks with Moselradweg first; selecting Moselradweg opened Track Details `#100002`; excluding it showed `1 track excluded`; restoring the API state and reloading returned Moselradweg to the highlight with no excluded-count badge. | PASS | `assets/TBS_11-drilldown-list.webp`, `assets/TBS_11-selected-track-opened.webp`, `assets/TBS_11-exclusion-dialog.webp`, `assets/TBS_11-excluded-count.webp`, `assets/TBS_11-highlight-restore.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| TBS-02 | P3 | Excluded-highlight count opens an empty track-browser search. | Exclude a track from a Stats highlight, then click the `1 track excluded` badge. | The excluded-count control should show the excluded tracks or a clear management view. | It switched to Tracks with search text `excluded highlights`, showing `0 of 10 tracks` even though one track was excluded. | `assets/TBS_11-excluded-count.webp` | Users can see that highlights are excluded, but the visible count affordance does not lead to a useful review/manage view. |

## Evidence Files

| File | Purpose |
|---|---|
| assets/TBS_11-drilldown-list.webp | Longest-track ranked drilldown. |
| assets/TBS_11-selected-track-opened.webp | Selected Moselradweg row opened Track Details `#100002`. |
| assets/TBS_11-exclusion-dialog.webp | Highlight exclusion dialog. |
| assets/TBS_11-excluded-count.webp | Excluded-count badge after temporary exclusion. |
| assets/TBS_11-highlight-restore.txt | Restoration note for temporary exclusion state. |

## Timings

| Step | Timing |
|---|---:|
| Highlight drilldown/open/exclusion check | <8m |

## Handoff Notes

- Completed: TBS_11 terminal PASS with issue TBS-02 recorded.
- Remaining unfinished coverage: continue with PLN_01.
- Blocked or not applicable: none.
- State left for the next packet: Moselradweg highlight exclusion restored; Track Details `#100002` remains open over Stats.
