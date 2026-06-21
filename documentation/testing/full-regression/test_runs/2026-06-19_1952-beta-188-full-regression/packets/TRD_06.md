# Packet: TRD_06

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: TRD_06
- In scope: Verify chart hover highlights matching mini-map point and mini-map hover highlights chart point, with no stale cursors after leaving.
- Out of scope: Static chart rendering and controls, covered by TRD_04/TRD_05.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_05.
- Required app/data state: Detail Graphs tab with mini-map and charts visible.
- Required browser context: desktop browser with visual/screenshot verification or DOM-visible hover state.

## Allowed Mutations

- Allowed: Move pointer over charts and mini-map.
- Not allowed: Change data or server state.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_06 | Targeted the first chart and detail mini-map by bounding box, moved pointer over chart, mini-map, and away, then inspected DOM for tooltip/crosshair/hover/popup state. | Hovering either surface visibly highlights the corresponding point on the other surface, and leaving clears the cursor/highlight. | Pointer moves executed and chart/map surfaces remained rendered, but no DOM-visible hover signal appeared and screenshot/canvas inspection is unavailable, so visual sync could not be verified. | BLOCKED | [assets/TRD_06-hover-sync-blocked.txt](../assets/TRD_06-hover-sync-blocked.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_06-hover-sync-blocked.txt](../assets/TRD_06-hover-sync-blocked.txt) | Hover targets, pointer actions, DOM observations, and blocking rationale. |

## Screenshot Evidence

No screenshot asset was captured for this packet; inability to visually verify canvas/SVG hover synchronization is the blocking condition.

## Timings

| Step | Timing |
|---|---:|
| Hover target/action attempt | ~3 min |

## Handoff Notes

- Completed: TRD_06 as terminal BLOCKED.
- Remaining unfinished coverage: TRD_07 onward.
- Blocked or not applicable: Unblock with a working screenshot/canvas inspection path or manual visual verification of chart/mini-map hover sync.
- State left for the next packet: Browser remains on Track #100005 Graphs.
