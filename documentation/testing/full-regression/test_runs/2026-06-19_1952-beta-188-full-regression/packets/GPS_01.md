# Packet: GPS_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: GPS_01
- In scope: Verify whether browser geolocation live checks apply on the configured quick-install target.
- Out of scope: HTTPS/localhost geolocation permission acceptance, live marker, follow mode, and stop behavior.

## Prerequisites

- Required previous coverage IDs or run packets: HMO_03
- Required app/data state: Authenticated map view on `http://188.245.169.80:18080/mtl/`
- Required browser context: Fresh desktop Chrome context against the remote plain-HTTP target.

## Allowed Mutations

- Allowed: Open the map and click the GPS control.
- Not allowed: Change server data, browser geolocation permissions, or target deployment configuration.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GPS_01 | Opened the remote quick-install target, captured the origin/security facts, and clicked the GPS control. | Remote plain-HTTP quick-install origins should be treated as not applicable for live browser geolocation checks; the app should communicate the secure-origin requirement. | The target URL was `http://188.245.169.80:18080/mtl/`, `window.isSecureContext` was `false`, geolocation permission state was `denied`, and clicking GPS showed `GPS unavailable` with the HTTPS/localhost requirement while the map remained usable. | NOT APPLICABLE | [assets/GPS-geolocation-insecure-context.webp](../assets/GPS-geolocation-insecure-context.webp); [assets/GPS-geolocation-insecure-context.txt](../assets/GPS-geolocation-insecure-context.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/GPS-geolocation-insecure-context.webp](../assets/GPS-geolocation-insecure-context.webp) | Remote plain-HTTP map after clicking GPS, showing the secure-origin warning. |
| [assets/GPS-geolocation-insecure-context.txt](../assets/GPS-geolocation-insecure-context.txt) | Captured origin, secure-context, geolocation permission, and visible warning facts. |

## Screenshot Evidence

![GPS unavailable on plain HTTP](../assets/GPS-geolocation-insecure-context.webp)

## Timings

| Step | Timing |
|---|---:|
| Open map, click GPS, capture evidence | 2026-06-20T00:54 CEST |

## Handoff Notes

- Completed: GPS_01 is terminal for this configured remote plain-HTTP run.
- Remaining unfinished coverage: GPS_02.
- Blocked or not applicable: Live geolocation permission/marker checks require HTTPS or localhost.
- State left for the next packet: Map state unchanged; shared GPS evidence can be reused for the rest of the GPS applicability rows.
