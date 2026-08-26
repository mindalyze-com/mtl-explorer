# Packet: MAP_07

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MAP_07
- In scope: High-zoom direction arrows with Track Points & Direction enabled on a suitable multi-point track.
- Out of scope: Sparse two-point/off-viewport fixtures.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_06 and DAT_07-DAT_08.
- Required app/data state: Six-point Bern track fixtures retained; Track points and direction enabled.
- Required browser context: Loaded map in the in-app browser.

## Allowed Mutations

- Allowed: Inspect the visible data-layer setting and use location search to zoom to Bern.
- Not allowed: Substitute an unsuitable sparse track.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_07 | Confirm Track points and direction enabled at 100%, then select Bern at 100 m scale over the six-point synthetic tracks. | Direction arrows appear on visible in-viewport point vertices. | Required setting, high zoom, suitable tracks, and rendered map were established. Arrows are canvas-rendered; ACC_04 prevents reliable visual confirmation. | BLOCKED | [assets/MAP_07-direction-arrows.txt](../assets/MAP_07-direction-arrows.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

No product defect is asserted; arrow rendering is visually unobservable with the run's blocked capture surface.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_07-direction-arrows.txt](../assets/MAP_07-direction-arrows.txt) | Enabled setting, opacity, high-zoom location, suitable track basis, and blocked assertion. |

## Screenshot Evidence

BLOCKED by ACC_04. Direction arrows are drawn in the map canvas and are absent from the accessible DOM.

## Timings

| Step | Timing |
|---|---:|
| Settings and high-zoom preparation | <3 min |

## Handoff Notes

- Completed: Correct setting and valid high-zoom test state.
- Remaining unfinished coverage: None; terminally blocked for canvas-arrow visual confirmation.
- Blocked or not applicable: BLOCKED by ACC_04.
- State left for the next packet: Map centered on Bern at 100 m with Track points and direction enabled.
