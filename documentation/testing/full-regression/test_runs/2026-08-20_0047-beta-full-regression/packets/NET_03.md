# Packet: NET_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: NET_03
- In scope: Login redirect on first-party server 401 and 403 responses.

## Prerequisites

- Required previous coverage IDs or run packets: NET_02.
- Required app/data state: Active signed-in disposable session.
- Required browser context: Protected Admin/Statistics routes.

## Allowed Mutations

- Allowed: Invalidate only the current disposable web session and sign in again.
- Not allowed: Change deployed security roles/configuration merely to fabricate a 403.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| NET_03 | Invalidated the exact current server session, activated a protected tool, observed the real 401 redirect, then audited live 403 reachability and signed back in. | Both 401 and 403 from the server redirect to login. | 401 redirected to `/mtl/login?reason=expired` and re-login passed. A real app 403 is not producible because this single-role install has no forbidden endpoint/account path. | BLOCKED | [assets/NET_03-auth-status.txt](../assets/NET_03-auth-status.txt) |

## Issues

No new live product issue; the 403 child check lacks a reachable server path in this configuration.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/NET_03-auth-status.txt](../assets/NET_03-auth-status.txt) | Exact live 401 flow, recovery, and 403 configuration blocker. |

## Screenshot Evidence

Accessible login state is recorded as text; ACC_04 blocks screenshots.

## Timings

| Step | Timing |
|---|---:|
| Protected action to expired-login route | About 4.7 s |
| Re-login to map | About 1.7 s |

## Handoff Notes

- Completed: Direct server 401 redirect and session recovery.
- Remaining unfinished coverage: None for NET_03.
- Blocked or not applicable: Real first-party 403 response path.
- State left for the next packet: Fresh signed-in session; map and eight tracks restored.
