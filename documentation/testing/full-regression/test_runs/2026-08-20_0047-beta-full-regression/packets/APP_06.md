# Packet: APP_06

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: APP_06
- In scope: Every available map style can be selected independently with light and dark UI themes.

## Prerequisites

- Required previous coverage IDs or run packets: APP_04/APP_05 UI theme state and MAP style controls.
- Required app/data state: Automatic map source; normal map layers.
- Required browser context: Map style settings and Admin Preferences.

## Allowed Mutations

- Allowed: Select all seven styles under each UI theme; leave OSM Dark selected for APP_07.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| APP_06 | Selected all seven map themes in dark UI, switched only UI to light, and selected all seven again. | Map style is independent and every style works with either UI theme. | All 14 selections became checked under the intended root UI theme; OSM Dark persisted through the intervening UI-only switch. | PASS | [assets/APP_06-map-theme-independence.txt](../assets/APP_06-map-theme-independence.txt) |

## Issues

No new issue.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/APP_06-map-theme-independence.txt](../assets/APP_06-map-theme-independence.txt) | Seven-by-two selection matrix and independence proof. |

## Screenshot Evidence

Direct radio/root state is durable; rendered basemap pixels remain uncaptured under ACC_04.

## Timings

| Step | Timing |
|---|---:|
| Each seven-style sequence | About 3.5 s |
| UI-only switch/reopen | About 18 s |

## Handoff Notes

- Completed: All seven map themes under both UI themes and independence across UI switch.
- Remaining unfinished coverage: None for APP_06.
- Blocked or not applicable: Durable basemap screenshots only.
- State left for the next packet: Light UI; Map style panel open; OSM Dark selected; Automatic source.
