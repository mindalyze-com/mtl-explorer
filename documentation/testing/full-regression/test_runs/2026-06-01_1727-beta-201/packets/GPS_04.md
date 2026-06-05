# Packet: GPS_04

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: GPS_04
- In scope: User-visible handling when geolocation is denied or disabled on this target.
- Out of scope: Live GPS marker/follow behavior; not applicable on remote HTTP.

## Prerequisites

- Required previous coverage IDs or run packets: GPS_03.
- Required app/data state: Remote plain-HTTP quick-install target.
- Required browser context: Fresh authenticated desktop Chromium context.

## Allowed Mutations

- Allowed: Click GPS tool and observe toast/console behavior.
- Not allowed: Change server data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GPS_04 | Clicked the GPS tool on a plain-HTTP non-loopback origin after the client fix. | Permission denied or disabled geolocation state shows a clear, actionable message. | The app displayed `GPS unavailable` with HTTPS/localhost guidance, did not display `GPS started`, and did not leave the GPS tool enabled. | FIXED | [assets/GPS_01-insecure-origin.txt](../assets/GPS_01-insecure-origin.txt); [assets/GPS_01-insecure-origin-gps-panel.webp](../assets/GPS_01-insecure-origin-gps-panel.webp); [assets/GPS_04-fixed-local-retest.txt](../assets/GPS_04-fixed-local-retest.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| MTL-FR-005 | P2 | GPS insecure-origin failure is now shown to the user. | Load MTL Explorer on a plain-HTTP non-loopback URL, sign in, click GPS. | The UI explains that browser geolocation requires HTTPS or localhost, or otherwise reports the disabled/denied state clearly. | Fixed: the app shows `GPS unavailable` with HTTPS/localhost guidance, does not show `GPS started`, and does not leave GPS enabled. | [assets/GPS_04-fixed-local-retest.txt](../assets/GPS_04-fixed-local-retest.txt) | FIXED |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/GPS_01-insecure-origin.txt](../assets/GPS_01-insecure-origin.txt) | Original failing secure-origin failure and app toast/console summary. |
| [assets/GPS_01-insecure-origin-gps-panel.webp](../assets/GPS_01-insecure-origin-gps-panel.webp) | Original failing screenshot showing `GPS started` toast after insecure-origin rejection. |
| [assets/GPS_04-fixed-local-retest.txt](../assets/GPS_04-fixed-local-retest.txt) | 2026-06-04 retest summary showing the fixed insecure-origin message and verification commands. |

## Screenshot Evidence

**Original failing screenshot showing GPS started toast after insecure-origin rejection.**

![Original failing screenshot showing GPS started toast after insecure-origin rejection.](../assets/GPS_01-insecure-origin-gps-panel.webp)

## Timings

| Step | Timing |
|---|---:|
| GPS disabled/error-state check | ~15 s |

## Handoff Notes

- Completed: GPS_04 retested and terminal as `FIXED`.
- Remaining unfinished coverage: Continue with GPS_05.
- Blocked or not applicable: Live marker/follow checks still require localhost or HTTPS.
- State left for the next packet: Server data unchanged.
