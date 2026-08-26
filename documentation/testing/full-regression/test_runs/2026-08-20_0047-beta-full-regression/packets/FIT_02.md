# Packet: FIT_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FIT_02
- In scope: Verify FIT acceptance, successful indexing, map display, browser search, and statistics inclusion.
- Out of scope: Full FIT detail-tab parity and downloads, covered by FIT_03-FIT_05.

## Prerequisites

- Required previous coverage IDs or run packets: FIT_01.
- Required app/data state: Activity.fit present in watched folder.
- Required browser context: Signed-in desktop context with freshness reload available.

## Allowed Mutations

- Allowed: Apply visible freshness Reload; search and open the FIT result.
- Not allowed: Convert or repair the sample outside the product flow.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FIT_02 | Observe watcher/converter/index result, reload freshness, search Activity.fit, and open the result. | FIT converts and indexes successfully, appears on map/browser, and changes statistics. | GPSBabel conversion and track 100005 ingest succeeded. UI moved to six tracks; search returned one FIT-backed 3.60 km result with a mapped detail. | PASS | [assets/FIT_02-index-ui.txt](../assets/FIT_02-index-ui.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FIT_02-index-ui.txt](../assets/FIT_02-index-ui.txt) | Watcher, conversion, index, freshness, search, stats, and map-detail evidence. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; exact log and visible UI values are recorded in the linked asset.

## Timings

| Step | Timing |
|---|---:|
| FIT conversion and ingest | 1.66 s |
| Freshness reload, search, and detail check | 2 min |

## Handoff Notes

- Completed: FIT conversion/indexing and required map/browser/statistics visibility.
- Remaining unfinished coverage: None for FIT_02.
- Blocked or not applicable: None.
- State left for the next packet: FIT-backed track 100005 details are open.
