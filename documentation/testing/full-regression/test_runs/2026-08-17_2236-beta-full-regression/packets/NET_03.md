# Packet: NET_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: NET_03
- In scope: Server authentication failures and browser redirect to login.
- Out of scope: Authorization policy for individual admin roles.

## Prerequisites

- Required previous coverage IDs or run packets: NET_02.
- Required app/data state: Running services and an authenticated session.
- Required browser context: Desktop browser capable of signing out and back in.

## Allowed Mutations

- Allowed: End the test session, probe one protected endpoint without valid auth, and sign in again.
- Not allowed: Store credentials/tokens or change an account.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| NET_03 | Probed a protected endpoint with no auth and an invalid bearer, signed out via Admin → Session, navigated to the protected app root, then signed in again. | A 401/403 from the server redirects to login. | Both protected endpoint probes returned HTTP 401. Sign-out moved to `/mtl/login`, and unauthenticated navigation to `/mtl/` redirected back to `/mtl/login`. Re-login restored the 15-track map. | PASS | [assets/NET_03-auth-redirect.txt](../assets/NET_03-auth-redirect.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/NET_03-auth-redirect.txt](../assets/NET_03-auth-redirect.txt) | Exact status codes, redirect URLs, login controls, and recovered session; no credentials. |

## Screenshot Evidence

Text evidence is sufficient and avoids capturing authentication entry.

## Timings

| Step | Timing |
|---|---:|
| 401 probes | Under 0.3 seconds total |
| Redirect to login | About 1.2 seconds |
| Re-login to map | About 1.6 seconds |

## Handoff Notes

- Completed: 401 response, protected-route redirect, login screen, and authenticated recovery.
- Remaining unfinished coverage: None for NET_03.
- Blocked or not applicable: None.
- State left for the next packet: Authenticated desktop map, 15 tracks, all services running.
