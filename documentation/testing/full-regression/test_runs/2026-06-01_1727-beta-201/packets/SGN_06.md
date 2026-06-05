# Packet: SGN_06

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: SGN_06
- In scope: Verify startup splash appears and disappears after map/tracks load.
- Out of scope: Startup failure retry behavior; covered by SGN_07.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_02.
- Required app/data state: Eleven visible tracks.
- Required browser context: Fresh signed-out browser context.

## Allowed Mutations

- Allowed: Sign in and capture startup/loaded states.
- Not allowed: Change app data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_06 | Signed in and captured the immediate startup state plus the later loaded map state. | Splash screen displays during startup and disappears once map and tracks load. | Startup text showed `LOADING YOUR TRAILS`; later map text no longer had loading text and showed `11 Tracks`. | PASS | [assets/SGN_06-splash-disappears.txt](../assets/SGN_06-splash-disappears.txt), [assets/SGN_06-startup-splash.webp](../assets/SGN_06-startup-splash.webp), [assets/SGN_06-map-loaded.webp](../assets/SGN_06-map-loaded.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SGN_06-splash-disappears.txt](../assets/SGN_06-splash-disappears.txt) | Splash and loaded-state assertions. |
| [assets/SGN_06-startup-splash.webp](../assets/SGN_06-startup-splash.webp) | Startup splash screenshot. |
| [assets/SGN_06-map-loaded.webp](../assets/SGN_06-map-loaded.webp) | Loaded map screenshot after splash disappears. |

## Screenshot Evidence

**Startup splash screenshot.**

![Startup splash screenshot.](../assets/SGN_06-startup-splash.webp)

**Loaded map screenshot after splash disappears.**

![Loaded map screenshot after splash disappears.](../assets/SGN_06-map-loaded.webp)

## Timings

| Step | Timing |
|---|---:|
| Splash capture after sign-in | ~0.2 seconds |
| Loaded map capture | ~5.5 seconds later |

## Handoff Notes

- Completed: SGN_06 terminal as `PASS`.
- Remaining unfinished coverage: Continue with SGN_07.
- Blocked or not applicable: None.
- State left for the next packet: App state unchanged.
