# Packet: GPS_05

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: GPS_05
- In scope: Disabling live GPS removes the marker and stops updates.
- Out of scope: Other coverage IDs except where noted as shared state/evidence.

## Prerequisites

- Required previous coverage IDs or run packets: GPS_01 through GPS_04 terminal; target is remote plain HTTP.
- Required app/data state: Current run-state and packet set for this run.
- Required browser context: As specified by the coverage action.

## Allowed Mutations

- Allowed: Confirm applicability constraint, reuse direct secure-origin evidence, and update GPS_05 packet/run-state.
- Not allowed: Collapse this ID into a parent row or skip required direct evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GPS_05 | Reviewed whether live GPS could be enabled before testing the disable/stop-updates flow. | On a secure origin after GPS is enabled, disabling GPS removes the marker and stops updates. | NOT APPLICABLE: remote plain HTTP prevents creating an active live GPS marker, so the disable-after-enabled flow cannot execute in this run. The plan directs live GPS rows to localhost or HTTPS. | NOT APPLICABLE | [assets/GPS_01-remote-http-gps.webp](../assets/GPS_01-remote-http-gps.webp); [assets/GPS_01-remote-http-gps.txt](../assets/GPS_01-remote-http-gps.txt) |

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

- Completed: This coverage ID is terminal as NOT APPLICABLE for the configured run.\n- Blocked or not applicable: requires localhost or HTTPS plus an active live GPS marker.\n- State left for the next packet: unchanged.
