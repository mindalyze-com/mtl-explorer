# Packet: GPS_03

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: GPS_03
- In scope: Follow-me GPS recentering and drifted state.
- Out of scope: Other coverage IDs except where noted as shared state/evidence.

## Prerequisites

- Required previous coverage IDs or run packets: GPS_01 and GPS_02 terminal; target is remote plain HTTP.
- Required app/data state: Current run-state and packet set for this run.
- Required browser context: As specified by the coverage action.

## Allowed Mutations

- Allowed: Confirm applicability constraint, reuse direct secure-origin evidence, and update GPS_03 packet/run-state.
- Not allowed: Collapse this ID into a parent row or skip required direct evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GPS_03 | Reviewed whether live geolocation could be enabled on the remote HTTP origin before testing follow-me behavior. | On a secure origin with live location, Follow me keeps the map centered until the user pans away. | NOT APPLICABLE: Follow me depends on an active live GPS marker, but remote plain HTTP prevents live geolocation in Chromium. The plan directs this row to localhost or HTTPS. | NOT APPLICABLE | [assets/GPS_01-remote-http-gps.webp](../assets/GPS_01-remote-http-gps.webp); [assets/GPS_01-remote-http-gps.txt](../assets/GPS_01-remote-http-gps.txt) |

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
| Applicability check | ~5 seconds |

## Handoff Notes

- Completed: This coverage ID is terminal as NOT APPLICABLE for the configured run.\n- Blocked or not applicable: requires localhost or HTTPS plus an active live GPS position.\n- State left for the next packet: unchanged.
