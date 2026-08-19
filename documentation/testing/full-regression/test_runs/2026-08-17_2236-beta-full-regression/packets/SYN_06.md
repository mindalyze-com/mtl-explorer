# Packet: SYN_06

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: SYN_06
- In scope: Logout/login does not cause repeated automatic freshness reloads.
- Out of scope: Invalid credentials, covered by SGN_03.

## Prerequisites

- Required previous coverage IDs or run packets: SYN_05.
- Required app/data state: Pending freshness change applied once; 15-track sync.
- Required browser context: Desktop Admin Session and login form.

## Allowed Mutations

- Allowed: One normal sign-out/sign-in cycle and passive observation.
- Not allowed: Hard reload or another server-side mutation during observation.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SYN_06 | Synced once, signed out, signed in, and observed the resolved map across 20 seconds of polling. | Login does not re-trigger automatic data refresh repeatedly. | Map resolved once at 15 Tracks and stayed stable; no banner, Fresh data loaded alert, or loading overlay appeared initially or after 20 s. | PASS | [assets/SYN_06-session-refresh.txt](../assets/SYN_06-session-refresh.txt); [assets/SYN_06-stable-login.jpg](../assets/SYN_06-stable-login.jpg) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SYN_06-session-refresh.txt](../assets/SYN_06-session-refresh.txt) | Session routes and initial/20-second freshness UI states. |
| [assets/SYN_06-stable-login.jpg](../assets/SYN_06-stable-login.jpg) | Stable authenticated 15-track map after observation. |

## Screenshot Evidence

- The full viewport preserves the settled 15-track map with no freshness banner
  or loading overlay after the session cycle.

## Timings

| Step | Timing |
|---|---:|
| Sign out/sign in | About 3 s |
| Post-login observation | 20 s |

## Handoff Notes

- Completed: Logout/login produced one stable load and no refresh loop.
- Remaining unfinished coverage: None for SYN_06.
- Blocked or not applicable: None.
- State left for the next packet: Authenticated root map, in sync at 15 Tracks.
