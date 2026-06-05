# Packet: ACC_04

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: ACC_04
- In scope: Verify compact screenshot evidence is being captured for representative working functions.
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
| ACC_04 | Captured and size-checked WebP screenshots for the signed-out login and signed-in map in RUN_SETUP. | Reports include compact screenshots for working functions, not only failures; each WebP remains below 85 KB. | RUN_SETUP-login.webp is 29,432 bytes and RUN_SETUP-map.webp is 56,132 bytes; both are linked and embedded in RUN_SETUP.md. | PASS | [assets/RUN_SETUP-login.webp](../assets/RUN_SETUP-login.webp); [assets/RUN_SETUP-map.webp](../assets/RUN_SETUP-map.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [assets/RUN_SETUP-login.webp](../assets/RUN_SETUP-login.webp) | Screenshot evidence |
| [assets/RUN_SETUP-map.webp](../assets/RUN_SETUP-map.webp) | Screenshot evidence |

## Screenshot Evidence

![assets/RUN_SETUP-login.webp](../assets/RUN_SETUP-login.webp)
![assets/RUN_SETUP-map.webp](../assets/RUN_SETUP-map.webp)

## Timings

| Step | Timing |
|---|---:|
| Packet execution | <1 minute |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
