# Packet: MAP_12

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MAP_12
- In scope: Applicable Swiss official route popup and close behavior.
- Out of scope: Permanent route-overlay setting changes.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_11.
- Required app/data state: Swiss route overlays offered by the active map configuration.
- Required browser context: Signed-in main map centered in Switzerland.

## Allowed Mutations

- Allowed: Temporarily enable Swiss route layers, navigate to a known official-route junction, click, close, and restore overlays.
- Not allowed: Change server configuration.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_12 | Enable four Swiss layers; click the Bern Bärenpark route junction; close the resulting popup. | Nearby official routes appear and popup closes cleanly. | `Nearby Routes` listed ViaBerna #38 and trail 3782104 with SchweizMobil/swisstopo attribution; Close removed the popup while the map stayed interactive. | PASS | [assets/MAP_12-swiss-routes.txt](../assets/MAP_12-swiss-routes.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_12-swiss-routes.txt](../assets/MAP_12-swiss-routes.txt) | Overlay availability, attribution, popup contents, close result, and restoration. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; exact accessible popup content and close state are linked above.

## Timings

| Step | Timing |
|---|---:|
| Route enable, locate, popup, close, and restore | <8 min |

## Handoff Notes

- Completed: Swiss official route popup and clean close.
- Remaining unfinished coverage: None for MAP_12.
- Blocked or not applicable: None.
- State left for the next packet: Route overlays restored to none; baseline nine-track map active.
