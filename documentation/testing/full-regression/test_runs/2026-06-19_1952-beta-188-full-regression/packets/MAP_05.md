# Packet: MAP_05

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MAP_05
- In scope: Verify zooming in on tracks improves detail/precision and does not break or duplicate lines.
- Out of scope: Direction-arrow marker behavior, covered by MAP_07/MAP_11.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_04.
- Required app/data state: Current 11-track dataset.
- Required browser context: desktop root map tab.

## Allowed Mutations

- Allowed: Use map zoom controls; query read-only simplified geometry API.
- Not allowed: Change data, preferences, or server state.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_05 | Clicked Zoom in four times on the root map and compared coarse/fine simplified geometry API responses. | Zooming in improves detail/precision and does not create duplicate or broken map lines. | Map scale changed from 500 km to 100 km, 11-track overlay count stayed present, console warnings/errors were empty, and simplified geometry returned more point-like coordinates as precision tightened while preserving 11 tracks. | PASS | [assets/MAP_05-zoom-precision.txt](../assets/MAP_05-zoom-precision.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_05-zoom-precision.txt](../assets/MAP_05-zoom-precision.txt) | Browser zoom state and simplified geometry precision comparison. |

## Screenshot Evidence

No screenshot asset was captured for this packet; direct DOM/API evidence is recorded in the text asset.

## Timings

| Step | Timing |
|---|---:|
| Zoom and precision checks | ~2 min |

## Handoff Notes

- Completed: MAP_05.
- Remaining unfinished coverage: MAP_06 onward.
- Blocked or not applicable: none.
- State left for the next packet: Root map tab remains zoomed in to a 100 km scale; reset/open fresh tab if a broader starting view is needed.
