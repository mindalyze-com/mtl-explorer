# Packet: MAP_07

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MAP_07
- In scope: Verify direction arrows appear on tracks at high zoom when Track Points & Direction is enabled.
- Out of scope: Track-point popup metrics, covered by MAP_11.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_06.
- Required app/data state: Current 11-track dataset.
- Required browser context: desktop map tab.

## Allowed Mutations

- Allowed: Open Map settings and zoom the map.
- Not allowed: Change data or server state.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_07 | Opened Maps and data, confirmed Track Points & Direction layer copy and opacity slider, verified slider at 100%, zoomed to 300 m scale, then attempted to verify arrows. | Direction arrows are visibly rendered on in-viewport track points at high zoom. | Layer/control prerequisites were present and enabled, but direct arrow visibility could not be verified because arrows render inside the canvas and the screenshot/canvas-inspection evidence path was unavailable in this browser session. | BLOCKED | [assets/MAP_07-direction-layer-blocked.txt](../assets/MAP_07-direction-layer-blocked.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_07-direction-layer-blocked.txt](../assets/MAP_07-direction-layer-blocked.txt) | Layer control, high-zoom state, and blocking evidence path. |

## Screenshot Evidence

No screenshot asset was captured for this packet; inability to capture or inspect the canvas is the blocking condition.

## Timings

| Step | Timing |
|---|---:|
| Layer/high-zoom attempt | ~5 min |

## Handoff Notes

- Completed: MAP_07 as terminal BLOCKED.
- Remaining unfinished coverage: MAP_08 onward.
- Blocked or not applicable: Direct arrow visibility requires a working screenshot/canvas inspection path or manual visual verification.
- State left for the next packet: Map settings tab remains open at 300 m scale with Track Points & Direction opacity at 100%.
