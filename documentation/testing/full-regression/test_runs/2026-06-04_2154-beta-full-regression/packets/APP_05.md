# Packet: APP_05

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: APP_05
- In scope: Hard refresh behavior while dark mode is selected.
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
| APP_05 | Started a fresh browser context with localStorage color scheme set to dark before app bootstrap, recorded theme mutation events during load/login, and captured the hard-refresh result. | Hard refresh in dark mode does not first apply/flash the light theme. | No theme event observed data-theme=light; a dark mutation/final dark state was observed, and the loaded app screenshot remained dark. | PASS | [assets/APP_05-hard-refresh-dark.webp](../assets/APP_05-hard-refresh-dark.webp); [assets/APP_05-no-light-flash.txt](../assets/APP_05-no-light-flash.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [assets/APP_05-hard-refresh-dark.webp](../assets/APP_05-hard-refresh-dark.webp) | Screenshot evidence |
| [assets/APP_05-no-light-flash.txt](../assets/APP_05-no-light-flash.txt) | Text/log evidence |

## Screenshot Evidence

![assets/APP_05-hard-refresh-dark.webp](../assets/APP_05-hard-refresh-dark.webp)

## Timings

| Step | Timing |
|---|---:|
| Hard-refresh probe | ~10 seconds |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
