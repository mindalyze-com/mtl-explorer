# Packet: IMP_06

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: IMP_06
- In scope: Find every imported public GPX by exact name in the browser, open its map/detail view, and confirm filter/statistics visibility.
- Out of scope: Point popup and map zoom interaction, covered by IMP_07.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_05.
- Required app/data state: Five imported GPX tracks; freshness state in sync.
- Required browser context: Signed-in desktop context.

## Allowed Mutations

- Allowed: Search the visible track browser and open/close detail views.
- Not allowed: Change or delete imported data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| IMP_06 | Search each exact imported name in Statistics > Tracks and open the matching row. Cross-check the unfiltered browser and overview evidence from IMP_05. | Each file is findable by name and represented in map/detail, statistics, and filter-aware browser views. | All five searches returned 1 of 5 tracks. Each result opened the expected Track Details ID/name and an embedded map. The unfiltered track browser and Statistics Overview contain all five. | PASS | [assets/IMP_06-per-file.txt](../assets/IMP_06-per-file.txt); [assets/IMP_05-reload.txt](../assets/IMP_05-reload.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/IMP_06-per-file.txt](../assets/IMP_06-per-file.txt) | Exact name, imported ID, detail URL, and map presence for each file. |
| [assets/IMP_05-reload.txt](../assets/IMP_05-reload.txt) | Cross-view all-five visibility and totals. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; direct visible-state values are recorded in the linked assets.

## Timings

| Step | Timing |
|---|---:|
| Five exact-name searches and detail checks | 7 min |

## Handoff Notes

- Completed: Exact-name lookup and detail/map identity verification for all five public GPX imports.
- Remaining unfinished coverage: None for IMP_06.
- Blocked or not applicable: None.
- State left for the next packet: Browser is on track 100004 details; imported data is unchanged.
