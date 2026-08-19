# Packet: MED_30

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_30
- In scope: Desktop viewer navigation, Nearby, zoom/pan/reset, Details, metadata, location-map layout/controls, and main-map handoff.
- Out of scope: Mobile viewer layout, covered by MED_31.

## Prerequisites

- Required previous coverage IDs or run packets: MED_29.
- Required app/data state: Six-photo correlated activity with route and positions.
- Required browser context: Signed-in desktop activity viewer.

## Allowed Mutations

- Allowed: Viewer-only interaction and map camera movement.
- Not allowed: Media or activity persistence changes.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_30 | Exercised side controls, keyboard, swipe, direct Nearby selection, collapse/expand, image zoom/pan/reset, Details, metadata, location map, and Open on main map. | Every desktop viewer interaction works; navigation does not zoom; details/location are complete; main-map handoff closes viewer at the photo. | All required interactions passed. Measured transforms stayed at scale 1 through navigation, zoom/pan/reset behaved exactly, metadata and lower-right map were complete, and main-map handoff centered the selected photo. | PASS | [assets/MED_30-desktop-viewer.txt](../assets/MED_30-desktop-viewer.txt) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_30-desktop-viewer.txt](../assets/MED_30-desktop-viewer.txt) | Navigation state, exact image transforms, metadata, controls, and handoff result. |

## Screenshot Evidence

- Live screenshots showed the expanded dark desktop viewer, blue/brown circular location markers, fitted route map, expanded/collapsed Nearby dock, and final 100 m main-map handoff. Exact semantic and transform evidence is linked above.

## Timings

| Step | Timing |
|---|---:|
| Each photo navigation | Under 400 ms |
| Nearby collapse/expand | Under 200 ms each |
| Zoom/pan/reset feedback | Under 200 ms each |
| Main-map handoff | Under 4 s including map settling |

## Handoff Notes

- Completed: All MED_30 desktop requirements.
- Remaining unfinished coverage: None for MED_30.
- Blocked or not applicable: None.
- State left for the next packet: Root map at the selected photo, viewer closed, 1280 x 720.
