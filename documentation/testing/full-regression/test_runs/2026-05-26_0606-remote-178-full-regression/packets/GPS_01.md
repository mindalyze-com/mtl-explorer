# Packet: GPS_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: GPS_01
- In scope: Verify whether this quick-install run is a secure origin suitable for live browser geolocation checks.
- Out of scope: Accepting a browser location permission prompt or validating a live GPS marker.

## Prerequisites

- Required previous coverage IDs or run packets: HMO_03
- Required app/data state: Remote quick-install app remains available with the current imported dataset.
- Required browser context: Signed-in desktop browser on `http://178.105.173.254:18080/mtl/`.

## Allowed Mutations

- Allowed: Open the GPS tool and inspect browser origin/geolocation availability.
- Not allowed: Introduce HTTPS, proxy through localhost, or accept browser location permission prompts.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GPS_01 | Verified the active app URL/security context and opened the GPS tool. | Remote plain-HTTP quick-install runs should be treated as non-secure origins; live GPS permission/marker rows are not applicable unless tested on localhost or HTTPS. | The app was loaded from `http://178.105.173.254:18080/mtl/`, not localhost or HTTPS. Browser geolocation was not exposed in the page context. Opening GPS produced only a transient app info toast and no usable browser permission/marker validation path. | PASS | `assets/GPS_01-secure-context.txt`, `assets/GPS_01-gps-panel-http.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/GPS_01-secure-context.txt | Recorded target origin and geolocation availability for the remote plain-HTTP run. |
| assets/GPS_01-gps-panel-http.webp | Screenshot of the GPS tool opened on the remote plain-HTTP app. |

## Timings

| Step | Timing |
|---|---:|
| Origin/geolocation inspection and GPS panel screenshot | <1 min |

## Handoff Notes

- Completed: GPS_01 confirms the expected remote plain-HTTP geolocation limitation.
- Remaining unfinished coverage: GPS_02 through RUN_CLEANUP.
- Blocked or not applicable: GPS_02-GPS_05 should be closed individually as `NOT APPLICABLE` for this run unless a secure-origin context is introduced.
- State left for the next packet: GPS tool has been opened; map remains usable with the imported dataset.
