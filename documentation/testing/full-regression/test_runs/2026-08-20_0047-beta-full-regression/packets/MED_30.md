# Packet: MED_30

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_30
- In scope: Desktop six-photo activity viewer navigation, filmstrip, Details, image viewport, location map, and Open on main map.
- Out of scope: Narrow phone layouts, covered by MED_31.

## Prerequisites

- Required previous coverage IDs or run packets: MED_29 cleanup.
- Required app/data state: Activity #100028 with six correlated photos and two correlated videos; no temporary manual or unknown position state.
- Required browser context: Authenticated desktop activity Media tab.

## Allowed Mutations

- Allowed: Ephemeral viewer selection, zoom, pan, panel, and map-camera state.
- Not allowed: Media, activity, position, time, or filesystem data changes.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_30 | Exercised side buttons, keyboard arrows, bidirectional pointer swipes, direct filmstrip selection, collapse/expand, zoom/pan/reset, Details hide/show, both requested time-source labels, the lower-right location map, and Open on main map. | Every desktop viewer path works; navigation never zooms; metadata and fitted location context remain usable; Open on main map closes at the selected photo. | All requested paths passed. Side/keyboard/swipe navigation selected the expected items at scale 1; direct selection and collapse/expand worked; zoom reached scale 2, drag changed translation, and Reset restored scale 1. Details returned after hiding. GPS and camera-clock timestamps were explicit. The lower-right map showed the full activity/photo context with only standard +/- controls. Open on main map closed the viewer and focused a Selected photo location marker at 100 m. | PASS | [assets/MED_30-desktop-viewer.txt](../assets/MED_30-desktop-viewer.txt) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_30-desktop-viewer.txt](../assets/MED_30-desktop-viewer.txt) | Exact navigation sequence, image transforms, Details/time-source observations, layout bounds, location controls, and main-map outcome. |

## Screenshot Evidence

Live screenshots confirmed the lower-right fitted activity/photo map and the selected-photo main-map focus. ACC_04 prevents saving the in-app result as a durable local image, so the linked text capture is the durable evidence.

## Timings

| Step | Timing |
|---|---:|
| Viewer navigation transitions | Under 1 s each |
| Details and map transitions | Under 1 s each |

## Handoff Notes

- Completed: Every MED_30 desktop viewer requirement.
- Remaining unfinished coverage: None for MED_30.
- Blocked or not applicable: Durable screenshot saving remains blocked by ACC_04, but it did not prevent the end-user interaction checks.
- State left for the next packet: Root main map with 8 Tracks and Selected photo location focused; data baseline unchanged.
