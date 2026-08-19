# Packet: IMP_06

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: IMP_06
- In scope: Verify each imported file by mapped track name in browser search, map selection/details, statistics, and a filter result.
- Out of scope: The separate TBS_02 source-file-path search requirement.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_05.
- Required app/data state: Five imported tracks loaded in all views.
- Required browser context: Filter Review tracks and Track Details.

## Allowed Mutations

- Allowed: Search names and open/close track details.
- Not allowed: Change or delete imported tracks.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| IMP_06 | Searched each mapped imported track name independently, opened its result, recorded the user-facing ID/URL and mini-map, and cross-checked all five in Statistics and the active five-track Filter result. | Each imported source appears by name in browser search, on the map, in statistics, and in at least one filter result. | Every exact name query returned one track; results opened IDs 100000-100004 with a selected-track mini-map; all five names appear in statistics and remain in the 5-track current filter/main map. | PASS | [assets/IMP_06-per-file.txt](../assets/IMP_06-per-file.txt); [assets/IMP_05-reload.txt](../assets/IMP_05-reload.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/IMP_06-per-file.txt](../assets/IMP_06-per-file.txt) | Per-source name, ID, search, map/detail, statistics, and filter evidence. |
| [assets/IMP_05-reload.txt](../assets/IMP_05-reload.txt) | Five-name statistics and filter totals. |
| [assets/TBS_02-file-search.txt](../assets/TBS_02-file-search.txt) | Incidental file-path-search result reserved for TBS_02. |

## Screenshot Evidence

Blocked by ACC_04; direct DOM and route evidence is recorded.

## Timings

| Step | Timing |
|---|---:|
| Five exact-name searches and detail openings | About 35 s |

## Handoff Notes

- Completed: Each source-to-track mapping is directly verified across required views.
- Remaining unfinished coverage: None for IMP_06.
- Blocked or not applicable: Screenshot capture blocked under ACC_04.
- State left for the next packet: Track 100003 details open from Filter Review tracks; all five source files remain present.
