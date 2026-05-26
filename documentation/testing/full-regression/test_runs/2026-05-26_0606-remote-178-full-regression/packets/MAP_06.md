# Packet: MAP_06

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MAP_06
- In scope: fast pan/zoom map interaction.
- Out of scope: browser-history console issue already captured in SGN_09.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_01.
- Required app/data state: signed-in app with tracks visible.
- Required browser context: desktop browser.

## Allowed Mutations

- Allowed: wheel zoom and drag pan.
- Not allowed: data changes.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_06 | Performed repeated wheel zooms and drag pans, then waited for the map to settle. | Fast pan/zoom does not leave stale lines, missing tiles, or runaway loading spinners. | Map remained usable with 10-track overlay and no visible loading/spinner text. Request log showed tile/API aborts from rapid movement but no frozen UI. | PASS | `assets/MAP_06-fast-pan-zoom.webp`, `assets/MAP_06-fast-pan-zoom.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/MAP_06-fast-pan-zoom.webp | Map after fast pan/zoom stress. |
| assets/MAP_06-fast-pan-zoom.txt | Visible text, console, and request-failure summary. |

## Timings

| Step | Timing |
|---|---:|
| Pan/zoom stress | <1m |

## Handoff Notes

- Completed: MAP_06 terminal PASS.
- Remaining unfinished coverage: continue with MAP_07.
- Blocked or not applicable: none.
- State left for the next packet: no data changed.
