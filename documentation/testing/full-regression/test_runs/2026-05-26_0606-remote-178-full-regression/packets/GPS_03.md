# Packet: GPS_03

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: GPS_03
- In scope: Determine applicability of follow-me recentering/drifted-state behavior.
- Out of scope: Secure-origin live GPS simulation.

## Prerequisites

- Required previous coverage IDs or run packets: GPS_01, GPS_02
- Required app/data state: Remote plain-HTTP quick-install app.
- Required browser context: Signed-in desktop browser on the target app.

## Allowed Mutations

- Allowed: Reuse GPS_01 origin/geolocation evidence.
- Not allowed: Change browser permissions or target origin.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GPS_03 | Assessed whether follow-me mode can be verified in this configured run. | With a live geolocation stream on a secure origin, follow-me should keep the map centered until manual panning creates a drifted state. | This run has no secure-origin geolocation path because it is remote plain HTTP. Without a live locate marker/position stream, follow-me recentering cannot be meaningfully exercised here. | NOT APPLICABLE | `assets/GPS_01-secure-context.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/GPS_01-secure-context.txt | Shared evidence for the remote plain-HTTP geolocation limitation. |

## Timings

| Step | Timing |
|---|---:|
| Applicability review | <1 min |

## Handoff Notes

- Completed: GPS_03 is terminal `NOT APPLICABLE` for this run.
- Remaining unfinished coverage: GPS_04 through RUN_CLEANUP.
- Blocked or not applicable: Test follow-me behavior on localhost or HTTPS.
- State left for the next packet: No app state mutation.
