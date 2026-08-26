# Packet: SYN_06

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: SYN_06
- In scope: Logout/relogin does not repeatedly trigger automatic data refresh.

## Prerequisites

- Required previous coverage IDs or run packets: SYN_05 followed by explicit synchronization.
- Required app/data state: Synchronized 8-track client/server state.
- Required browser context: Signed-in map and Admin Session.

## Allowed Mutations

- Allowed: Credentials-only sign out and documented-credential sign in.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SYN_06 | Signed out, signed back in, sampled four polling windows, then inspected Data status. | Relogin does not repeatedly trigger automatic refresh. | Re-login restored 8 Tracks; no banner appeared through 25.345 s; Data status remained In sync/Healthy. | PASS | [assets/SYN_06-relogin.txt](../assets/SYN_06-relogin.txt) |

## Issues

No new issue.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SYN_06-relogin.txt](../assets/SYN_06-relogin.txt) | Logout/login routes, polling samples, and final sync state. |

## Screenshot Evidence

Live desktop inspection confirmed login, map, and Data status. ACC_04 prevents durable screenshot saving.

## Timings

| Step | Timing |
|---|---:|
| Sign out and re-login | About 1.4 s |
| Post-login observation | 25.345 s |

## Handoff Notes

- Completed: Logout/relogin and repeated freshness-poll observation.
- Remaining unfinished coverage: None for SYN_06.
- Blocked or not applicable: Durable screenshots only.
- State left for the next packet: Admin Data status open; 8-track client/server in sync.
