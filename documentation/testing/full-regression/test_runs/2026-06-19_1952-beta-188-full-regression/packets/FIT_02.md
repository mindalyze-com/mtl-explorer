# Packet: FIT_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: FIT_02
- In scope: Verify the imported FIT activity is accepted/indexed, displayed on the map, searchable in the track browser, and included in statistics.
- Out of scope: FIT detail tabs and download/export behavior.

## Prerequisites

- Required previous coverage IDs or run packets: FIT_01.
- Required app/data state: `Activity.fit` copied into the watched import folder; three remaining GPX tracks after deletion.
- Required browser context: desktop browser authenticated as the README quick-start user.

## Allowed Mutations

- Allowed: Refresh the browser to pick up imported data and search within the track browser.
- Not allowed: Add, delete, or edit track files.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FIT_02 | Monitored the import/index status after copying `Activity.fit`, refreshed the app, opened Stats > Tracks, and searched for `Activity.fit`. | FIT file is accepted, indexed successfully, visible on the map, searchable in the browser, and included in statistics. | FIT-backed track `100005` indexed with `COMPLETED_WITH_SUCCESS` / `SUCCESS`, 3,600 trackpoints, and source file `Activity.fit`; map header shows `4 Tracks`; stats overview includes `Track 100005` as a Walking activity; track browser search for `Activity.fit` returns exactly `Track 100005`. | PASS | [assets/FIT_02-import-monitor.txt](../assets/FIT_02-import-monitor.txt); [assets/FIT_02-search-stats.webp](../assets/FIT_02-search-stats.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FIT_02-import-monitor.txt](../assets/FIT_02-import-monitor.txt) | Import/index polling summary for the FIT-backed track. |
| [assets/FIT_02-search-stats.webp](../assets/FIT_02-search-stats.webp) | Browser evidence showing `Activity.fit` search result, `Track 100005`, and `4 Tracks` after refresh. |

## Screenshot Evidence

![FIT import searchable in track browser](../assets/FIT_02-search-stats.webp)

## Timings

| Step | Timing |
|---|---:|
| FIT import/index wait | 67.5 s |
| Browser refresh and search check | <1 min |

## Handoff Notes

- Completed: FIT_02.
- Remaining unfinished coverage: FIT_03 onward.
- Blocked or not applicable: none.
- State left for the next packet: `Activity.fit` is imported as track `100005`; browser is on Stats > Tracks filtered by `Activity.fit`.
