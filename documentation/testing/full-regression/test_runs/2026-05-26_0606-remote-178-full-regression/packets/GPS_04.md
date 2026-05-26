# Packet: GPS_04

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: GPS_04
- In scope: Determine applicability of browser permission-denied/disabled GPS messaging.
- Out of scope: Secure-origin permission denial testing.

## Prerequisites

- Required previous coverage IDs or run packets: GPS_01 through GPS_03
- Required app/data state: Remote plain-HTTP quick-install app.
- Required browser context: Signed-in desktop browser on the target app.

## Allowed Mutations

- Allowed: Reuse GPS_01 origin/geolocation evidence.
- Not allowed: Change target origin or browser geolocation permissions.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GPS_04 | Assessed whether permission-denied/disabled GPS messaging can be tested on the configured target. | On a secure origin, denying or disabling GPS permission should show a clear message. | The configured target is remote plain HTTP, so the browser permission-denied flow for live geolocation is outside this run's applicable scope per GPS_01 and the test plan note. | NOT APPLICABLE | `assets/GPS_01-secure-context.txt`, `assets/GPS_01-gps-panel-http.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/GPS_01-secure-context.txt | Shared evidence for the remote plain-HTTP geolocation limitation. |
| assets/GPS_01-gps-panel-http.webp | GPS tool evidence captured on the configured target. |

## Timings

| Step | Timing |
|---|---:|
| Applicability review | <1 min |

## Handoff Notes

- Completed: GPS_04 is terminal `NOT APPLICABLE` for this run.
- Remaining unfinished coverage: GPS_05 through RUN_CLEANUP.
- Blocked or not applicable: Test permission denial on localhost or HTTPS.
- State left for the next packet: No app state mutation.
