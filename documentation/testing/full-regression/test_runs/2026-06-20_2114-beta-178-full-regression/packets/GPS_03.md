# Packet: GPS_03

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: GPS_03
- In scope: Determine whether GPS follow-me and drifted-map behavior can be tested on this target.
- Out of scope: HTTPS/localhost live follow-me behavior.

## Prerequisites

- Required previous coverage IDs or run packets: GPS_02 terminal.
- Required app/data state: Authenticated map view on the remote quick-install target.
- Required browser context: Desktop Chromium context against `http://178.104.209.132:18080/mtl/`.

## Allowed Mutations

- Allowed: Click the GPS control and inspect whether live GPS starts.
- Not allowed: Override geolocation permission, spoof a secure origin, or change deployment configuration.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GPS_03 | Checked whether follow-me mode could be exercised after attempting GPS on the remote plain-HTTP target. | On a secure origin, active GPS should keep the map centered until the user pans away. | The configured target could not start GPS because it is a remote plain-HTTP origin; clicking GPS showed the secure-origin warning, so no live marker/follow state existed to pan away from. | NOT APPLICABLE | [assets/GPS-geolocation-insecure-context.webp](../assets/GPS-geolocation-insecure-context.webp); [assets/GPS-geolocation-insecure-context.txt](../assets/GPS-geolocation-insecure-context.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
|  |  |  |  |  |  |  |  |

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
| Reused GPS applicability evidence | <1 min |

## Handoff Notes

- Completed: GPS_03 is terminal as not applicable for this remote plain-HTTP run.
- Remaining unfinished coverage: GPS_04 onward.
- Blocked or not applicable: Follow-me and drifted-state checks require live GPS on HTTPS or localhost.
- State left for the next packet: Map state unchanged.
