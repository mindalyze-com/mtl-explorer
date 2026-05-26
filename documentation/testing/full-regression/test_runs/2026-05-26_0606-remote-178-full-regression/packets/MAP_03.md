# Packet: MAP_03

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MAP_03
- In scope: newly imported tracks appearing after freshness/reload prompt without browser restart.
- Out of scope: exact per-format visibility defects already covered by FMT/MAP_02.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_02-IMP_05 and FMT_01.
- Required app/data state: imported source files and freshness banner available.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: accept freshness reload.
- Not allowed: restart browser or server for this check.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_03 | Accepted/reloaded from data freshness after imports and observed map/list update. | Newly imported tracks appear without a full browser restart. | Freshness reload after GPX import updated the map from baseline to imported data; later hard reload after format imports showed the expanded 10-track visible dataset. | PASS | `assets/IMP_05-map-after-freshness-refresh.webp`, `assets/FMT_01-all-track-browser-after-unique.webp`, `assets/FMT_01-unique-format-status.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/IMP_05-map-after-freshness-refresh.webp | Map after accepting GPX import freshness refresh. |
| assets/FMT_01-all-track-browser-after-unique.webp | Track browser after format imports/reload. |
| assets/FMT_01-unique-format-status.txt | Format import watcher/status lines. |

## Timings

| Step | Timing |
|---|---:|
| Freshness reload observation | <1m |

## Handoff Notes

- Completed: MAP_03 terminal PASS.
- Remaining unfinished coverage: continue with MAP_04.
- Blocked or not applicable: none.
- State left for the next packet: no data changed.
