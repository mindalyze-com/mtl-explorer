# Packet: GPS_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: GPS_02
- In scope: Determine applicability of the live GPS permission prompt and locate-marker check for this target.
- Out of scope: Testing live geolocation on localhost or HTTPS.

## Prerequisites

- Required previous coverage IDs or run packets: GPS_01
- Required app/data state: Remote plain-HTTP quick-install app.
- Required browser context: Signed-in desktop browser on `http://178.105.173.254:18080/mtl/`.

## Allowed Mutations

- Allowed: Reuse GPS_01 origin/geolocation evidence.
- Not allowed: Change the target origin or accept a browser location permission prompt.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GPS_02 | Assessed whether enabling GPS can produce a browser permission prompt and locate marker on the configured target. | On a secure origin, enabling GPS should show a permission prompt and, after acceptance, display a locate marker. | This run is on a remote plain-HTTP origin, confirmed by GPS_01. The plan explicitly marks live GPS permission/marker checks not applicable for this target type. | NOT APPLICABLE | `assets/GPS_01-secure-context.txt`, `assets/GPS_01-gps-panel-http.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/GPS_01-secure-context.txt | Shared evidence that the configured run is remote plain HTTP and not suitable for live geolocation. |

## Timings

| Step | Timing |
|---|---:|
| Applicability review | <1 min |

## Handoff Notes

- Completed: GPS_02 is terminal `NOT APPLICABLE` for this run.
- Remaining unfinished coverage: GPS_03 through RUN_CLEANUP.
- Blocked or not applicable: Live GPS prompt/marker behavior should be tested on localhost or HTTPS.
- State left for the next packet: No app state mutation.
