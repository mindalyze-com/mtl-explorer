# Packet: GPS_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: GPS_01
- In scope: GPS secure-origin applicability on the remote quick-install URL.
- Out of scope: Other coverage IDs except where noted as shared state/evidence.

## Prerequisites

- Required previous coverage IDs or run packets: Remote browser access to the quick-install app over the documented URL.
- Required app/data state: Current run-state and packet set for this run.
- Required browser context: As specified by the coverage action.

## Allowed Mutations

- Allowed: Open the GPS screen, inspect browser geolocation context, capture evidence, and update GPS_01 packet/run-state.
- Not allowed: Collapse this ID into a parent row or skip required direct evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GPS_01 | Loaded the remote app at http://167.233.16.201:18080/mtl/, opened GPS, checked window.isSecureContext and attempted navigator.geolocation.getCurrentPosition. | Remote plain-HTTP origin is not a secure browser context, so live GPS checks require localhost or HTTPS. | PASS: the page origin was http://167.233.16.201:18080, isSecureContext was false, direct geolocation returned Only secure origins are allowed, and the app displayed a GPS unavailable HTTPS/localhost message. | PASS | [assets/GPS_01-remote-http-gps.webp](../assets/GPS_01-remote-http-gps.webp); [assets/GPS_01-remote-http-gps.txt](../assets/GPS_01-remote-http-gps.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [assets/GPS_01-remote-http-gps.webp](../assets/GPS_01-remote-http-gps.webp) | Screenshot evidence |
| [assets/GPS_01-remote-http-gps.txt](../assets/GPS_01-remote-http-gps.txt) | Text/log evidence |

## Screenshot Evidence

![assets/GPS_01-remote-http-gps.webp](../assets/GPS_01-remote-http-gps.webp)

## Timings

| Step | Timing |
|---|---:|
| GPS secure-origin probe | ~15 seconds |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
