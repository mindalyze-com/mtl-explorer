# Packet: SRC_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: SRC_01
- In scope: Open location search, type a place name, and verify results appear.
- Out of scope: Selecting a result or clearing the marker.

## Prerequisites

- Required previous coverage IDs or run packets: GPS_05
- Required app/data state: Main map available with imported tracks.
- Required browser context: Signed-in desktop browser.

## Allowed Mutations

- Allowed: Open the search panel and enter a query.
- Not allowed: Change imported data or app configuration.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SRC_01 | Opened Location Search and typed `Bern`. | Search results appear for the typed place name. | Results appeared in the search panel, headed by `Bern, Switzerland`, followed by additional matching places. | PASS | `assets/SRC_01-search-results.webp`, `assets/SRC_01-search-results.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/SRC_01-search-results.webp | Screenshot of the populated search results list. |
| assets/SRC_01-search-results.txt | Compact text capture of representative results. |

## Timings

| Step | Timing |
|---|---:|
| Search open, query, result wait | <1 min |

## Handoff Notes

- Completed: SRC_01 passed.
- Remaining unfinished coverage: SRC_02 through RUN_CLEANUP.
- Blocked or not applicable: None.
- State left for the next packet: Search panel remains open with query `Bern` and visible results.
