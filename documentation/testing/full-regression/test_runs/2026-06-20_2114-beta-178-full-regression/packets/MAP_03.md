# Packet: MAP_03

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MAP_03
- In scope: Newly imported tracks appear on map surfaces without a browser restart after reload.
- Out of scope: per-file map click behavior; covered by IMP_07 and MAP_08.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_02 through IMP_05.
- Required app/data state: required GPX import flow completed.
- Required browser context: authenticated desktop browser.

## Allowed Mutations

- Allowed: use the completed import/reload evidence.
- Not allowed: import additional tracks for this duplicate coverage check.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_03 | Reused the completed required import flow evidence: after five GPX files indexed, the same browser session used Admin Helpers `Reload` and rechecked map/filter/stats/browser surfaces. | Newly imported tracks from the required data-change flow appear without a full browser restart after accepting the freshness/reload prompt. | PASS: without a full browser restart, the reloaded map and filter showed `5 Tracks`, Stats Overview showed `5 TRACKS`, Stats Tracks showed five rows, and the API confirmed track IDs `100000` through `100004`. | PASS | [packets/IMP_05.md](IMP_05.md); [assets/IMP_05-helper-reload.txt](../assets/IMP_05-helper-reload.txt); [assets/IMP_05-map-after-reload.webp](../assets/IMP_05-map-after-reload.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [packets/IMP_05.md](IMP_05.md) | Original helper reload and post-import surface verification packet. |
| [assets/IMP_05-helper-reload.txt](../assets/IMP_05-helper-reload.txt) | Reload and API count evidence. |
| [assets/IMP_05-map-after-reload.webp](../assets/IMP_05-map-after-reload.webp) | Map after reload showing imported tracks. |

## Screenshot Evidence

![Map after import reload](../assets/IMP_05-map-after-reload.webp)

## Timings

| Step | Timing |
|---|---:|
| Cross-reference assessment | ~2 seconds |

## Handoff Notes

- Completed: MAP_03 is terminal.
- Remaining unfinished coverage: MAP_04 onward.
- Blocked or not applicable: none.
- State left for the next packet: no new mutations for MAP_03.
