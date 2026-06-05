# Packet: GPS_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: GPS_01
- In scope: Secure-origin applicability for browser geolocation on this quick-install target.
- Out of scope: Live GPS marker/follow behavior; covered or marked not applicable in GPS_02 through GPS_05.

## Prerequisites

- Required previous coverage IDs or run packets: HMO_03.
- Required app/data state: Root map reachable on `http://167.233.16.201:18080/mtl/`.
- Required browser context: Fresh authenticated desktop Chromium context.

## Allowed Mutations

- Allowed: Open GPS tool and attempt browser geolocation.
- Not allowed: Change server data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GPS_01 | Loaded the app from the remote plain-HTTP target, checked browser secure-context state, attempted `navigator.geolocation.getCurrentPosition`, and opened the app GPS tool. | Remote plain-HTTP origin is not a secure context; live GPS rows should be treated as not applicable unless tested on localhost or HTTPS. | Browser reported `isSecureContext=false` for `http://167.233.16.201:18080`; geolocation failed with Chrome error `Only secure origins are allowed`; this run cannot exercise real GPS marker/follow behavior on the target origin. | PASS | [assets/GPS_01-insecure-origin.txt](../assets/GPS_01-insecure-origin.txt); [assets/GPS_01-insecure-origin-gps-panel.webp](../assets/GPS_01-insecure-origin-gps-panel.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/GPS_01-insecure-origin.txt](../assets/GPS_01-insecure-origin.txt) | Secure-context, geolocation API, GPS tool, and console summary. |
| [assets/GPS_01-insecure-origin-gps-panel.webp](../assets/GPS_01-insecure-origin-gps-panel.webp) | App map after GPS click on insecure HTTP origin. |

## Screenshot Evidence

**App map after GPS click on insecure HTTP origin.**

![App map after GPS click on insecure HTTP origin.](../assets/GPS_01-insecure-origin-gps-panel.webp)

## Timings

| Step | Timing |
|---|---:|
| Secure-origin and GPS click check | ~15 s |

## Handoff Notes

- Completed: GPS_01 terminal as `PASS`.
- Remaining unfinished coverage: Continue with GPS_02.
- Blocked or not applicable: Live geolocation rows require localhost or HTTPS to execute normally.
- State left for the next packet: Server data unchanged.
