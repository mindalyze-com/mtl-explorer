# Packet: GPS_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: GPS_02
- In scope: Live GPS enable flow and accepted permission marker behavior.
- Out of scope: Other coverage IDs except where noted as shared state/evidence.

## Prerequisites

- Required previous coverage IDs or run packets: GPS_01 terminal; target is remote plain HTTP.
- Required app/data state: Current run-state and packet set for this run.
- Required browser context: As specified by the coverage action.

## Allowed Mutations

- Allowed: Confirm applicability constraint, reuse direct secure-origin evidence, and update GPS_02 packet/run-state.
- Not allowed: Collapse this ID into a parent row or skip required direct evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GPS_02 | Attempted geolocation from the remote HTTP origin and reviewed the GPS screen. | On a secure origin, enabling GPS prompts for permission and shows a locate marker after accept. | NOT APPLICABLE: this target is a remote plain-HTTP origin, so Chromium blocks live geolocation before a usable permission/marker flow can run. The plan says to test this row on localhost or HTTPS. | NOT APPLICABLE | [assets/GPS_01-remote-http-gps.webp](../assets/GPS_01-remote-http-gps.webp); [assets/GPS_01-remote-http-gps.txt](../assets/GPS_01-remote-http-gps.txt) |

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

- Completed: This coverage ID is terminal as NOT APPLICABLE for the configured run.\n- Blocked or not applicable: requires localhost or HTTPS to exercise live GPS permission and marker behavior.\n- State left for the next packet: unchanged.
