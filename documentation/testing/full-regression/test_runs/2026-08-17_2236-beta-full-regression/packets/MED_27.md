# Packet: MED_27

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_27
- In scope: Cluster, map-view, activity, single-photo, paging, camera-preservation, and desktop/390 x 760 media map navigation.
- Out of scope: Activity timeline paging, covered by MED_28.

## Prerequisites

- Required previous coverage IDs or run packets: MED_26.
- Required app/data state: Six correlated synthetic media and visible main-map media layer.
- Required browser context: Signed-in map at desktop and 390 x 760.

## Allowed Mutations

- Allowed: Map pan/zoom and temporary layer visibility changes.
- Not allowed: Changing media rows or frozen fixtures.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_27 | Activated the live six-photo cluster and exercised This cluster, Current map view, and Photos along a GPS track. Then zoomed toward single-marker level and attempted the full phone repeat. | All three destinations and the single-photo branch work without changing the map camera; bounded adjacent pages load; the full flow also works at 390 x 760. | Desktop cluster/map/activity destinations passed with correct scope labels and unchanged camera. The phone capability remained 1280 x 720; MED_02 made markers disappear at local zoom; six fixtures cannot cross a 200-item page boundary. | BLOCKED | [assets/MED_27-browser-flow.txt](../assets/MED_27-browser-flow.txt) |

## Issues

- Existing FR-009 prevents a reachable separated-marker state after zoom.
- Browser viewport emulation did not change the live page size for this repeat.
- The frozen six-photo fixture cannot exercise adjacent 200-member paging.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_27-browser-flow.txt](../assets/MED_27-browser-flow.txt) | Exact chooser labels, destinations, camera result, blockers, and layer cleanup. |

## Screenshot Evidence

- Live screenshot inspection confirmed the six-member cluster and chooser states. The browser image result was not exposed as a durable local file, so the linked text capture is the durable evidence.

## Timings

| Step | Timing |
|---|---:|
| Each chooser/viewer transition | Under 1 s |
| Repeated map zoom settling | Under 2 s per zoom burst |

## Handoff Notes

- Completed: Desktop cluster, map-view, and multi-activity chooser destinations.
- Remaining unfinished coverage: None for MED_27; blocked children are recorded above.
- Blocked or not applicable: Phone viewport, single-marker branch, and adjacent-page loading.
- State left for the next packet: Normal GPS tracks, media, and trackpoints layers enabled; heatmap disabled.
