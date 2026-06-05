# Packet: LOC_03

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: LOC_03
- In scope: Selected locale persistence across reload.
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
| LOC_03 | With de-DE selected, reloaded the app, reopened Stats, and checked stored locale plus formatted totals. | Locale persists across reload. | After reload, localStorage mtl.locale remained de-DE and Stats still rendered German separators; no bad literals were visible. | PASS | [assets/LOC_03-de-de-after-reload.webp](../assets/LOC_03-de-de-after-reload.webp); [assets/LOC_03-locale-persistence.txt](../assets/LOC_03-locale-persistence.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [assets/LOC_03-de-de-after-reload.webp](../assets/LOC_03-de-de-after-reload.webp) | Screenshot evidence |
| [assets/LOC_03-locale-persistence.txt](../assets/LOC_03-locale-persistence.txt) | Text/log evidence |

## Screenshot Evidence

![assets/LOC_03-de-de-after-reload.webp](../assets/LOC_03-de-de-after-reload.webp)

## Timings

| Step | Timing |
|---|---:|
| Reload persistence | ~25 seconds |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
