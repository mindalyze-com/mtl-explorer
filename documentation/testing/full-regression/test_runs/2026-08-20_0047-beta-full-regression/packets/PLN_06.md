# Packet: PLN_06

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: PLN_06
- In scope: Elevation profile rendering and linked map hover marker.
- Out of scope: Saved-plan lifecycle.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_05.
- Required app/data state: Computed 5.13 km route.
- Required browser context: Desktop Planner with visible elevation chart.

## Allowed Mutations

- Allowed: Hover the interactive elevation series.
- Not allowed: Change or persist the route.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| PLN_06 | Inspect the rendered profile and hover its series midpoint. | Profile renders and hover highlights the matching map point. | The 19-point profile rendered; hover exposed a 2.40 km/639 m/+0.2% tooltip, chart crosshair, and a visible 14x14 Planner map hover marker. | PASS | [assets/PLN_06-profile-hover.txt](../assets/PLN_06-profile-hover.txt) |

## Issues

None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/PLN_06-profile-hover.txt](../assets/PLN_06-profile-hover.txt) | Profile, tooltip, crosshair, and linked map-marker evidence. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; DOM-backed chart and map marker evidence is linked above.

## Timings

| Step | Timing |
|---|---:|
| Profile render and hover | 1 min |

## Handoff Notes

- Completed: Elevation profile and map-hover linkage.
- Remaining unfinished coverage: None for PLN_06.
- Blocked or not applicable: None.
- State left for the next packet: One-leg route remains active.
