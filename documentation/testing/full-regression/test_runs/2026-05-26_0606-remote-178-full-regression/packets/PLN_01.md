# Packet: PLN_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: PLN_01
- In scope: open planner and select a routing profile.
- Out of scope: waypoint drawing and route calculation, covered by following planner packets.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_11.
- Required app/data state: map loaded; BRouter available if configured.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: open Planner and change planner profile.
- Not allowed: save a plan or change imported track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| PLN_01 | Opened Planner, expanded the profile selector, and changed the profile from Hiking to Road Bike. | Planner opens and a routing profile can be picked. | Planner opened in Drawing mode, showed `BRouter status: ready`, exposed Hiking/Road Bike/Mountain Hiking/Car profile options, and selected Road Bike. | PASS | `assets/PLN_01-planner-open.webp`, `assets/PLN_01-road-bike-selected.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/PLN_01-planner-open.webp | Planner opened with BRouter ready. |
| assets/PLN_01-road-bike-selected.webp | Road Bike profile selected. |

## Timings

| Step | Timing |
|---|---:|
| Planner open/profile selection | <3m |

## Handoff Notes

- Completed: PLN_01 terminal PASS.
- Remaining unfinished coverage: continue with PLN_02.
- Blocked or not applicable: none.
- State left for the next packet: Planner open in Drawing mode with Road Bike selected.
