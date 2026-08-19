# Packet: IMP_07

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: IMP_07
- In scope: Zoom to each imported track, click it and points, verify geometry, popups, selection/details, and absence of stale/duplicate lines.
- Out of scope: Non-map browser-list selection already covered in IMP_06.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_06.
- Required app/data state: Five imported tracks on main map.
- Required browser context: Unobstructed main map with visual canvas targeting.

## Allowed Mutations

- Allowed: Pan, zoom, click canvas tracks/points, and close selections.
- Not allowed: Guess PASS from counters or list navigation.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| IMP_07 | Verified five-track map count and one-to-one detail/mini-map navigation, returned to the main map, measured its viewport, and attempted direct canvas selection. | All five canvas tracks and points can be visually targeted to prove line geometry, popups, and clean selection state. | The canvas exposes no semantic line/point targets, the screenshot API is unavailable, and a direct map click focused the map without selecting a track. Accurate visual targeting of five lines/points is impossible without guessing; required geometry/popup evidence is blocked. | BLOCKED | [assets/IMP_07-map-canvas-constraint.txt](../assets/IMP_07-map-canvas-constraint.txt); [assets/IMP_06-per-file.txt](../assets/IMP_06-per-file.txt); [assets/ACC_04-screenshot-capability.txt](../assets/ACC_04-screenshot-capability.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/IMP_07-map-canvas-constraint.txt](../assets/IMP_07-map-canvas-constraint.txt) | Directly verified portions, canvas attempt, missing capability, and unblock path. |
| [assets/IMP_06-per-file.txt](../assets/IMP_06-per-file.txt) | One-to-one detail and mini-map navigation proof. |

## Screenshot Evidence

Blocked by ACC_04; that missing visual channel is the concrete constraint for this canvas-only check.

## Timings

| Step | Timing |
|---|---:|
| Return to map, viewport inspection, and direct canvas attempt | About 12 s |

## Handoff Notes

- Completed: Map count and per-track user-navigation/mini-map portions.
- Remaining unfinished coverage: None; terminally blocked for canvas line/point targeting in this environment.
- Blocked or not applicable: Requires functional visual screenshot/targeting or semantic canvas features; blocks this ID but not DOM-based regression.
- State left for the next packet: Main map unobstructed with all five tracks loaded.
