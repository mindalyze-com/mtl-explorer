# Packet: GPS_05

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: GPS_05
- In scope: Determine whether GPS disable/removal behavior can be tested on this target.
- Out of scope: Secure-origin live GPS stop behavior.

## Prerequisites

- Required previous coverage IDs or run packets: GPS_04
- Required app/data state: Authenticated map view on the remote quick-install target.
- Required browser context: Desktop Chrome context against `http://188.245.169.80:18080/mtl/`.

## Allowed Mutations

- Allowed: Click the GPS control.
- Not allowed: Change site origin, browser geolocation permissions, or deployment configuration.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GPS_05 | Checked whether the GPS disable flow could be exercised after attempting GPS on the remote plain-HTTP target. | On a secure origin after GPS starts, disabling GPS should remove the marker and stop updates. | GPS never started on the configured target because the origin was `http:` and `window.isSecureContext=false`; clicking GPS showed the HTTPS/localhost requirement, so no marker or watch existed to disable. | NOT APPLICABLE | [assets/GPS-geolocation-insecure-context.webp](../assets/GPS-geolocation-insecure-context.webp); [assets/GPS-geolocation-insecure-context.txt](../assets/GPS-geolocation-insecure-context.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/GPS-geolocation-insecure-context.webp](../assets/GPS-geolocation-insecure-context.webp) | Confirms the target prevents live GPS before marker/watch creation. |
| [assets/GPS-geolocation-insecure-context.txt](../assets/GPS-geolocation-insecure-context.txt) | Captured origin, permission state, and warning evidence reused for GPS applicability rows. |

## Screenshot Evidence

![GPS unavailable on plain HTTP](../assets/GPS-geolocation-insecure-context.webp)

## Timings

| Step | Timing |
|---|---:|
| Reused GPS applicability evidence | 2026-06-20T00:54 CEST |

## Handoff Notes

- Completed: GPS_05 is terminal as not applicable for this remote plain-HTTP run.
- Remaining unfinished coverage: SRC_01.
- Blocked or not applicable: GPS stop/removal requires a secure-origin live GPS watch/marker first.
- State left for the next packet: Map state unchanged; queue continues with location search.
