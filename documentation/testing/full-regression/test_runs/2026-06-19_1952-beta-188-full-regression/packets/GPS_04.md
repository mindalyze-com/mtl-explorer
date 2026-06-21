# Packet: GPS_04

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: GPS_04
- In scope: Determine whether the permission-denied or disabled GPS-state check applies on the configured target.
- Out of scope: User-denied browser permission flow on HTTPS/localhost.

## Prerequisites

- Required previous coverage IDs or run packets: GPS_03
- Required app/data state: Authenticated map view on the remote quick-install target.
- Required browser context: Desktop Chrome context against `http://188.245.169.80:18080/mtl/`.

## Allowed Mutations

- Allowed: Click the GPS control.
- Not allowed: Change site permissions or origin security.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GPS_04 | Clicked GPS on the configured remote plain-HTTP target and captured the app message. | On a secure origin, denying or disabling GPS should show a clear message. For this remote plain-HTTP run, live GPS permission checks are not applicable. | The browser reported `window.isSecureContext=false` and geolocation permission `denied`; the app showed `GPS unavailable` with `GPS needs HTTPS or localhost. Open MTL Explorer from a secure address to use live location.` The user-denied permission path still cannot be exercised on this origin. | NOT APPLICABLE | [assets/GPS-geolocation-insecure-context.webp](../assets/GPS-geolocation-insecure-context.webp); [assets/GPS-geolocation-insecure-context.txt](../assets/GPS-geolocation-insecure-context.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/GPS-geolocation-insecure-context.webp](../assets/GPS-geolocation-insecure-context.webp) | Shows the GPS unavailable toast on the remote plain-HTTP target. |
| [assets/GPS-geolocation-insecure-context.txt](../assets/GPS-geolocation-insecure-context.txt) | Documents secure-origin state, browser geolocation permission state, and visible warning text. |

## Screenshot Evidence

![GPS unavailable on plain HTTP](../assets/GPS-geolocation-insecure-context.webp)

## Timings

| Step | Timing |
|---|---:|
| Reused GPS applicability evidence | 2026-06-20T00:54 CEST |

## Handoff Notes

- Completed: GPS_04 is terminal as not applicable for the user-denied permission path on this remote plain-HTTP run.
- Remaining unfinished coverage: GPS_05.
- Blocked or not applicable: True browser permission-denied testing requires HTTPS or localhost.
- State left for the next packet: Map state unchanged.
