# Packet: FLT_05

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: FLT_05
- In scope: geo drawing modes for circle, rectangle, polygon; undo, cancel, finish, clear, and shape persistence.
- Out of scope: exact geospatial inclusion math.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_04.
- Required app/data state: Filter panel open with Base scope geo controls available.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: draw, cancel, finish, clear, and reload filter geo shapes.
- Not allowed: mutate track source files.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FLT_05 | Started circle drawing and cancelled; drew and cleared a circle; drew a rectangle; started polygon drawing, observed undo/finish controls, double-clicked to finish, then cleared rectangle and polygon. Also checked shape persistence after reload in FLT_04. | Circle, rectangle, and polygon drawing work; undo, cancel, finish, and clear all work; saved shapes reappear next time. | Cancel, circle draw, rectangle draw, polygon double-click finish, and clear worked. The explicit Polygon Undo and Finish buttons were disabled while the tool reported `3 points`, and the rectangle geo parameter did not reappear after reload. | FAIL | `assets/FLT_05-circle-start.webp`, `assets/FLT_05-circle-drawn.webp`, `assets/FLT_05-polygon-before-undo.webp`, `assets/FLT_05-polygon-finish-attempt.webp`, `assets/FLT_05-shapes-cleared.webp`, `assets/FLT_04-after-geo-reload-expanded.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| FLT-02 | P2 | Geo filter parameter is lost after reload. | Draw a rectangle geo filter area, reload, reopen Base scope. | Saved geo shape reappears after reload. | Area section returned to empty Draw buttons. | `assets/FLT_04-after-geo-reload-expanded.webp` | Users can lose spatial filtering after reload. |
| FLT-03 | P3 | Polygon tool disables explicit Undo/Finish controls while points are present. | Start Polygon draw, click three map points, inspect draw toolbar. | Undo and Finish controls are available when the polygon has enough points. | Toolbar reported `3 points`, but Undo and Finish were disabled; only double-click finish worked. | `assets/FLT_05-polygon-before-undo.webp`, `assets/FLT_05-polygon-finish-attempt.webp` | Keyboard/button users may be unable to finish or revise polygon drawing through visible controls. |

## Evidence Files

| File | Purpose |
|---|---|
| assets/FLT_05-circle-start.webp | Circle draw mode with Cancel control. |
| assets/FLT_05-circle-drawn.webp | Circle parameter active after drawing. |
| assets/FLT_05-rectangle-drawn.webp | Rectangle parameter active after drawing. |
| assets/FLT_05-polygon-before-undo.webp | Polygon tool reporting three points while Undo/Finish are disabled. |
| assets/FLT_05-polygon-finish-attempt.webp | Polygon parameter active after double-click finish. |
| assets/FLT_05-shapes-cleared.webp | Geo shapes cleared from Base scope. |
| assets/FLT_04-after-geo-reload-expanded.webp | Reload persistence failure for geo shape. |

## Timings

| Step | Timing |
|---|---:|
| Geo drawing controls | <8m |

## Handoff Notes

- Completed: FLT_05 terminal FAIL due disabled explicit polygon controls and geo persistence failure.
- Remaining unfinished coverage: continue with FLT_06.
- Blocked or not applicable: none.
- State left for the next packet: filter still active with keyword `Jura` and From date; geo shapes cleared.
