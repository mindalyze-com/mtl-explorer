# Packet: TBS_05

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TBS_05
- In scope: Data-row activation opens the corresponding track details and returns cleanly.
- Out of scope: Shape-preview map selection covered by FLT_20.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_04.
- Required app/data state: Statistics Tracks All view with Track 100005 available.
- Required browser context: Desktop track table.

## Allowed Mutations

- Allowed: Search, activate a row, close details, and clear search.
- Not allowed: Modify track details.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TBS_05 | Searched Track 100005, clicked its row, verified route/ID/content/map, closed details, and checked return state. | Row opens that track's details. | Route opened /mtl/track/100005 with #100005, Activity.fit, Walking, map, and matching metrics. Close returned to the one-row Statistics result with query preserved. | PASS | [assets/TBS_05-row-details.txt](../assets/TBS_05-row-details.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_05-row-details.txt](../assets/TBS_05-row-details.txt) | Search result, route, details identity/content, return state, and cleanup. |

## Screenshot Evidence

Unavailable under ACC_04. Exact route, accessible ID/name/metrics/map state, and restored query provide direct evidence.

## Timings

| Step | Timing |
|---|---:|
| Search and row navigation | About 5 s |
| Return and clear | About 5 s |

## Handoff Notes

- Completed: Row-to-corresponding-details navigation and return-state preservation.
- Remaining unfinished coverage: None for TBS_05.
- Blocked or not applicable: None.
- State left for the next packet: Statistics Tracks All view remains open with search cleared and 15 tracks.
