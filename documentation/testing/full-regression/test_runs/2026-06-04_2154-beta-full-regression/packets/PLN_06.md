# Packet: PLN_06

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: PLN_06
- In scope: Planner elevation chart hover synchronization.
- Out of scope: Other coverage IDs except where noted as shared state/evidence.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_05 PASS.
- Required app/data state: Current run-state and packet set for this run.
- Required browser context: As specified by the coverage action.

## Allowed Mutations

- Allowed: Move the pointer over the Planner elevation chart and capture hover state.
- Not allowed: Collapse this ID into a parent row or skip required direct evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| PLN_06 | Hovered over the Planner elevation chart and checked for a synchronized map hover marker. | Elevation chart hover produces a visible corresponding marker on the map. | One Highcharts chart was present and map hover markers became visible after moving over the elevation chart. | PASS | [assets/PLN_06-elevation-hover-marker.webp](../assets/PLN_06-elevation-hover-marker.webp); [assets/PLN_06-elevation-hover.txt](../assets/PLN_06-elevation-hover.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [assets/PLN_06-elevation-hover-marker.webp](../assets/PLN_06-elevation-hover-marker.webp) | Screenshot evidence |
| [assets/PLN_06-elevation-hover.txt](../assets/PLN_06-elevation-hover.txt) | Text/log evidence |

## Screenshot Evidence

![assets/PLN_06-elevation-hover-marker.webp](../assets/PLN_06-elevation-hover-marker.webp)

## Timings

| Step | Timing |
|---|---:|
| Packet execution | <1 minute |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
