# Packet: PLN_08

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: PLN_08.
- In scope: planned-route GPX download validity and route agreement.
- Out of scope: routing data failures, covered next.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_07.
- Required app/data state: four-leg route saved as Regression Plan PLN08.
- Required browser context: Planner Load GPX action.

## Allowed Mutations

- Allowed: save/export/delete one plan and inspect the downloaded file read-only.
- Not allowed: retain the plan or commit the downloaded route.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| PLN_08 | Exported the saved plan as GPX, validated XML and coordinates, compared length, and deleted the plan. | Download is valid GPX and matches the planned route. | Valid GPX 1.1 contained 426 elevation points and a 7,663.2 m path matching the 7.69 km route. | PASS | [validation](../assets/PLN_08-gpx-validation.txt) |

## Issues

No issue found.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/PLN_08-gpx-validation.txt](../assets/PLN_08-gpx-validation.txt) | XML, metadata, point count, bounds, and length validation. |

## Screenshot Evidence

The exported route is already shown in [PLN_06-chart-hover.webp](../assets/PLN_06-chart-hover.webp); file validation is textual.

## Timings

| Step | Timing |
|---|---:|
| Export | < 1 s |
| XML and geometry validation | < 1 s |

## Handoff Notes

- Completed: PLN_08 is terminal `PASS`.
- Remaining unfinished coverage: PLN_09 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: Planner Load empty; route remains available in Drawing; GPX artifact registered for final cleanup.
