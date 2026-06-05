# Packet: APP_07

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: APP_07
- In scope: Selected map style persistence across reload.
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
| APP_07 | Selected OSM Dark map style, recorded map settings, reloaded the app, reopened Map settings, and captured the selected style. | Selected map style persists across reload. | Map settings stored theme=dark before reload and after reload; Map settings screenshot after reload still showed OSM Dark selected. | PASS | [assets/APP_07-map-style-after-reload.webp](../assets/APP_07-map-style-after-reload.webp); [assets/APP_07-map-style-persistence.txt](../assets/APP_07-map-style-persistence.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [assets/APP_07-map-style-after-reload.webp](../assets/APP_07-map-style-after-reload.webp) | Screenshot evidence |
| [assets/APP_07-map-style-persistence.txt](../assets/APP_07-map-style-persistence.txt) | Text/log evidence |

## Screenshot Evidence

![assets/APP_07-map-style-after-reload.webp](../assets/APP_07-map-style-after-reload.webp)

## Timings

| Step | Timing |
|---|---:|
| Map style reload persistence | ~25 seconds |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
