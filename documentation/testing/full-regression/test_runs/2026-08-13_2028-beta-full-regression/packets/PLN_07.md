# Packet: PLN_07

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: PLN_07.
- In scope: save, list, load, and delete planned routes.
- Out of scope: GPX validity, covered next.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_06.
- Required app/data state: four-leg Road Bike route.
- Required browser context: Planner Drawing and Load.

## Allowed Mutations

- Allowed: create and delete one named saved plan.
- Not allowed: leave it stored.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| PLN_07 | Saved Regression Plan PLN07, listed it, loaded it, confirmed restored route, and deleted it. | Full saved-plan lifecycle works. | Save/list/load restored exact route and profile; confirmation deletion removed the only plan and empty state appeared. | PASS | [lifecycle](../assets/PLN_07-saved-plan.txt), [route](../assets/PLN_06-chart-hover.webp) |

## Issues

No issue found.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/PLN_07-saved-plan.txt](../assets/PLN_07-saved-plan.txt) | Exact save/list/load/delete states. |
| [assets/PLN_06-chart-hover.webp](../assets/PLN_06-chart-hover.webp) | Route shape and profile saved in the lifecycle. |

## Screenshot Evidence

The saved route's visual state is paired with exact lifecycle text.

## Timings

| Step | Timing |
|---|---:|
| Save/list/load/delete | < 1 s each |

## Handoff Notes

- Completed: PLN_07 is terminal `PASS`.
- Remaining unfinished coverage: PLN_08 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: Planner Load tab; no saved routes; loaded route remains in Drawing state.
