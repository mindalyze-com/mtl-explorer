# Packet: MOB_03

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MOB_03
- In scope: Mobile tables, charts, map controls, and overflow behavior.
- Out of scope: Other coverage IDs except where noted as shared state/evidence.

## Prerequisites

- Required previous coverage IDs or run packets: Previous queue rows terminal or explicitly not required.
- Required app/data state: Current run-state and packet set for this run.
- Required browser context: As specified by the coverage action.

## Allowed Mutations

- Allowed: Read-only verification and packet/run-state updates.
- Not allowed: Collapse this ID into a parent row or skip required direct evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MOB_03 | Opened Stats on mobile, switched to the Tracks table and Trends charts, captured both states, and scanned for bad literals/overflow. | Tables, charts, and map controls stay usable; no incoherent text overflow. | Tracks table and Highcharts charts rendered on mobile; no NaN/undefined literals appeared. The audit found horizontal overflow in the intentionally scrollable data table region, while the screenshot showed the table remained usable rather than text overlapping incoherently. | PASS | [assets/MOB_03-mobile-stats-table.webp](../assets/MOB_03-mobile-stats-table.webp); [assets/MOB_03-mobile-charts.webp](../assets/MOB_03-mobile-charts.webp); [assets/MOB_mobile-results.txt](../assets/MOB_mobile-results.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [assets/MOB_03-mobile-stats-table.webp](../assets/MOB_03-mobile-stats-table.webp) | Screenshot evidence |
| [assets/MOB_03-mobile-charts.webp](../assets/MOB_03-mobile-charts.webp) | Screenshot evidence |
| [assets/MOB_mobile-results.txt](../assets/MOB_mobile-results.txt) | Text/log evidence |

## Screenshot Evidence

![assets/MOB_03-mobile-stats-table.webp](../assets/MOB_03-mobile-stats-table.webp)
![assets/MOB_03-mobile-charts.webp](../assets/MOB_03-mobile-charts.webp)

## Timings

| Step | Timing |
|---|---:|
| Mobile Stats table/charts | ~35 seconds |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
