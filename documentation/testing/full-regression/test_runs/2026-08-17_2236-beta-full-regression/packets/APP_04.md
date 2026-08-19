# Packet: APP_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: APP_04
- In scope: Selected application theme persistence across reload and login.
- Out of scope: First-paint flash, covered by APP_05.

## Prerequisites

- Required previous coverage IDs or run packets: APP_03.
- Required app/data state: Dark selected; 15-track authenticated session.
- Required browser context: Statistics, Preferences, Session, login, root map.

## Allowed Mutations

- Allowed: Browser reload and one sign-out/sign-in cycle.
- Not allowed: Change theme during the persistence sequence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| APP_04 | Reloaded in Dark, verified Preferences, signed out, and signed in. | Selected theme persists across reload and login. | Dark remained checked after reload; Stats, login, and post-login map all computed `color-scheme: dark` on rgb(10,10,15). | PASS | [assets/APP_04-theme-persistence.txt](../assets/APP_04-theme-persistence.txt); [assets/APP_04-dark-after-login.jpg](../assets/APP_04-dark-after-login.jpg) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/APP_04-theme-persistence.txt](../assets/APP_04-theme-persistence.txt) | Reload, toggle, login-page, and post-login computed states. |
| [assets/APP_04-dark-after-login.jpg](../assets/APP_04-dark-after-login.jpg) | Settled dark map after login. |

## Screenshot Evidence

- The full viewport preserves the stable dark 15-track map after the session cycle.

## Timings

| Step | Timing |
|---|---:|
| Reload/theme check | About 2 s |
| Sign out/sign in | About 4 s |

## Handoff Notes

- Completed: Dark persists across reload and login.
- Remaining unfinished coverage: None for APP_04.
- Blocked or not applicable: None.
- State left for the next packet: Authenticated dark root map, 15 Tracks.
