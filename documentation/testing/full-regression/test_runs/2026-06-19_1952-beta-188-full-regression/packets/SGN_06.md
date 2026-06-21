# Packet: SGN_06

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: SGN_06
- In scope: Verify splash screen appears during startup and disappears after map/tracks load.
- Out of scope: Startup failure/retry behavior.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_05.
- Required app/data state: browser signed in; 11 tracks imported.
- Required browser context: desktop browser.

## Allowed Mutations

- Allowed: Reload the app.
- Not allowed: Change data or server state.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_06 | Reloaded the signed-in app and captured DOM during the transient startup state and after load settled. | Splash screen with logo/background/message displays during startup and disappears once map/tracks load. | Startup DOM included MTL Explorer branding and `Loading your trails`; loaded DOM showed map region and `11 Tracks`, with no `Loading your trails` remaining. | PASS | [assets/SGN_06-splash-load.txt](../assets/SGN_06-splash-load.txt); [assets/SGN_05-relogin-map.webp](../assets/SGN_05-relogin-map.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SGN_06-splash-load.txt](../assets/SGN_06-splash-load.txt) | DOM flags/excerpts for splash and loaded map states. |
| [assets/SGN_05-relogin-map.webp](../assets/SGN_05-relogin-map.webp) | Stable loaded map screenshot after sign-in. |

## Screenshot Evidence

![Loaded map after sign-in](../assets/SGN_05-relogin-map.webp)

## Timings

| Step | Timing |
|---|---:|
| Reload splash/load check | <1 min |

## Handoff Notes

- Completed: SGN_06.
- Remaining unfinished coverage: SGN_07 onward.
- Blocked or not applicable: none.
- State left for the next packet: Browser is signed in at `/mtl/`.
