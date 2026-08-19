# Packet: MED_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_01
- In scope: Default media layer and enabled/disabled persistence.
- Out of scope: Bounds behavior and pin viewer.

## Prerequisites

- Required previous coverage IDs or run packets: AVR_04.
- Required app/data state: Six indexed synthetic media points.
- Required browser context: Desktop Map settings.

## Allowed Mutations

- Allowed: Reset/toggle map-layer preference and reload.
- Not allowed: Media-data changes.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_01 | Reset map settings, verified media default-on, toggled off/reloaded, toggled on/reloaded, and read the local bounds result. | Media pins default on; off/on preferences survive reload. | Default showed media pressed/on and six local points; off survived reload as `Show Photos and media`; on survived reload as `Hide Photos and media`. | PASS | [assets/MED_01-layer-preference.txt](../assets/MED_01-layer-preference.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_01-layer-preference.txt](../assets/MED_01-layer-preference.txt) | Default state, reload persistence, and six-point bounds result. |

## Screenshot Evidence

Unavailable under ACC_04. Accessible layer state and live media bounds provide direct evidence.

## Timings

| Step | Timing |
|---|---:|
| Reset/default check | About 2 s |
| Disable/reload/enable/reload | About 6 s |

## Handoff Notes

- Completed: Media default and preference persistence.
- Remaining unfinished coverage: None for MED_01.
- Blocked or not applicable: None.
- State left for the next packet: Media enabled; Your data open after reload.

