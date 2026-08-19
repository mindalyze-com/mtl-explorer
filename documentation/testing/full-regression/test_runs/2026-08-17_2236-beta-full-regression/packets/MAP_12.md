# Packet: MAP_12

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MAP_12
- In scope: Applicable Swiss Mobility route overlay popup and clean close.
- Out of scope: Guessing a canvas route coordinate.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_11.
- Required app/data state: Bern-centered Swiss map and available Swiss route overlays.
- Required browser context: Signed-in map at 100 m scale.

## Allowed Mutations

- Allowed: Toggle live overlay/data visibility and restore with Reset.
- Not allowed: Persist changed map settings beyond this packet.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_12 | Enabled all four Swiss layers, verified live SchweizMobil/swisstopo attribution, isolated user-data layers, attempted controlled Bern clicks, and reset settings. | Nearby official routes appear in a popup that closes cleanly. | Applicable overlays activated and map remained interactive, but their canvas paths expose no semantic target. With screenshots blocked, no route line could be positively located; popup/close could not be exercised reliably. Reset restored defaults. | BLOCKED | [assets/MAP_12-swiss-routes.txt](../assets/MAP_12-swiss-routes.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_12-swiss-routes.txt](../assets/MAP_12-swiss-routes.txt) | Overlay controls, attribution, targetability constraint, and restoration evidence. |

## Screenshot Evidence

Required to locate an official route path but unavailable under ACC_04.

## Timings

| Step | Timing |
|---|---:|
| Enable four Swiss layers | About 2 s |
| Isolate user layers and attempt route interaction | About 10 s |
| Reset map settings and verify restoration | About 4 s |

## Handoff Notes

- Completed: Applicability, activation, attribution, interaction health, and settings restoration.
- Remaining unfinished coverage: None; terminally blocked for route-line popup target/close.
- Blocked or not applicable: ACC_04 plus canvas-only Swiss route geometry.
- State left for the next packet: Default map settings restored; Map overview panel open at Bern 100 m.
