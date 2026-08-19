# Packet: FLT_20

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FLT_20
- In scope: Review tracks footer placement and shared track-browser behavior across Filter review and Statistics Tracks.
- Out of scope: Quick-view curation semantics covered by later TBS packets.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_19.
- Required app/data state: 15 visible tracks; filter may be reset to baseline.
- Required browser context: Primary desktop tab plus temporary 390x844 mobile tab.

## Allowed Mutations

- Allowed: Reset filter, search, sort, paginate, select a map shape, open/close track details, and temporarily override viewport.
- Not allowed: Modify track records or leave a viewport override active.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FLT_20 | Reset the filter, opened Review tracks beside Reset filter, exercised search, summary, sorting, 10/5 pagination, map-shape selection, details, desktop table/mobile cards, then repeated shared controls under Statistics Tracks. | Review uses the same complete, responsive track browser as Statistics Tracks. | Both entry points exposed the same 15-track summary, search, sortable fields, track data, selection, and detail navigation. Review paginated 10/5 on desktop and rendered overflow-free cards at 390x844. Statistics search/sort and map selection also worked. | PASS | [assets/FLT_20-shared-track-browser.txt](../assets/FLT_20-shared-track-browser.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_20-shared-track-browser.txt](../assets/FLT_20-shared-track-browser.txt) | Filter footer, desktop/mobile Review, shared Statistics controls, selection, and details evidence. |

## Screenshot Evidence

Unavailable under ACC_04. Accessible states plus exact row counts, summaries, sort endpoints, page counts, viewport metrics, and active-selection DOM state provide direct evidence.

## Timings

| Step | Timing |
|---|---:|
| Desktop Review track-browser flow | About 20 s |
| Mobile responsive Review check | About 4 s |
| Statistics Tracks shared-component recheck | About 8 s |

## Handoff Notes

- Completed: Footer placement, shared search/summary/sort/pagination, desktop/mobile rendering, map selection, track details, and Statistics recheck.
- Remaining unfinished coverage: None for FLT_20.
- Blocked or not applicable: None.
- State left for the next packet: Filter is reset; Statistics Tracks is open with all 15 tracks; search is clear; viewport is 1280x720.
