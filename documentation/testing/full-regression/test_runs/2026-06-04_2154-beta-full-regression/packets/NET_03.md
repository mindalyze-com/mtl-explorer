# Packet: NET_03

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: NET_03
- In scope: 401 / 403 server response handling from an authenticated-looking browser context.
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
| NET_03 | Seeded localStorage with an invalid unexpired JWT, opened /mtl/, and waited for the first protected API response. | Server 401/403 responses redirect to login and do not leave the user in a broken authenticated shell. | The browser landed on http://167.233.16.201:18080/mtl/login, the Sign In form was visible, and localStorage no longer contained mtl.jwt. Assertions redirectedToLogin, loginVisible, and tokenCleared were true. | PASS | [NET_03-auth-redirect.webp](../assets/NET_03-auth-redirect.webp); [NET_03-auth-redirect.txt](../assets/NET_03-auth-redirect.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [NET_03-auth-redirect.webp](../assets/NET_03-auth-redirect.webp) | Screenshot evidence |
| [NET_03-auth-redirect.txt](../assets/NET_03-auth-redirect.txt) | Text/log evidence |

## Screenshot Evidence

![NET_03-auth-redirect.webp](../assets/NET_03-auth-redirect.webp)

## Timings

| Step | Timing |
|---|---:|
| Invalid-token startup | 15 seconds |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
