# Packet: GPS_03

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: GPS_03
- In scope: Determine whether follow-me recenter/drift behavior can be tested on this target.
- Out of scope: Secure-origin GPS simulation or HTTPS/localhost follow-mode validation.

## Prerequisites

- Required previous coverage IDs or run packets: GPS_02
- Required app/data state: Authenticated map view on the remote quick-install target.
- Required browser context: Desktop Chrome context against `http://188.245.169.80:18080/mtl/`.

## Allowed Mutations

- Allowed: Use the GPS control on the map.
- Not allowed: Change browser permissions or deployment scheme.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GPS_03 | Checked whether follow-me mode could be exercised after attempting GPS on the remote plain-HTTP target. | On a secure origin, active GPS should keep the map centered until the user pans away. | The configured target could not start GPS because it is a remote plain-HTTP origin; clicking GPS showed the secure-origin warning, so no live marker/follow state existed to pan away from. | NOT APPLICABLE | [assets/GPS-geolocation-insecure-context.webp](../assets/GPS-geolocation-insecure-context.webp); [assets/GPS-geolocation-insecure-context.txt](../assets/GPS-geolocation-insecure-context.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/GPS-geolocation-insecure-context.webp](../assets/GPS-geolocation-insecure-context.webp) | Shows the app refusing live GPS on the remote plain-HTTP origin. |
| [assets/GPS-geolocation-insecure-context.txt](../assets/GPS-geolocation-insecure-context.txt) | Documents `window.isSecureContext=false`, geolocation permission state, and visible warning text. |

## Screenshot Evidence

![GPS unavailable on plain HTTP](../assets/GPS-geolocation-insecure-context.webp)

## Timings

| Step | Timing |
|---|---:|
| Reused GPS applicability evidence | 2026-06-20T00:54 CEST |

## Handoff Notes

- Completed: GPS_03 is terminal as not applicable for this remote plain-HTTP run.
- Remaining unfinished coverage: GPS_04.
- Blocked or not applicable: Follow-me behavior requires an active GPS watch/marker, which requires HTTPS or localhost.
- State left for the next packet: Map state unchanged.
