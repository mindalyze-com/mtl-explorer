# Packet: GPS_05

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: GPS_05
- In scope: Determine applicability of live GPS disable/stop-updates behavior.
- Out of scope: Secure-origin live location stream setup.

## Prerequisites

- Required previous coverage IDs or run packets: GPS_01 through GPS_04
- Required app/data state: Remote plain-HTTP quick-install app.
- Required browser context: Signed-in desktop browser on the target app.

## Allowed Mutations

- Allowed: Reuse GPS_01 origin/geolocation evidence.
- Not allowed: Change target origin or browser geolocation permissions.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GPS_05 | Assessed whether disabling live GPS can be verified in this configured run. | With live GPS active, disabling GPS should remove the locate marker and stop position updates. | This remote plain-HTTP run cannot establish a live browser geolocation marker/stream. There is no applicable live GPS state to disable and verify. | NOT APPLICABLE | `assets/GPS_01-secure-context.txt` |

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

- Completed: GPS_05 is terminal `NOT APPLICABLE` for this run.
- Remaining unfinished coverage: SRC_01 through RUN_CLEANUP.
- Blocked or not applicable: Test GPS disable/stop-updates behavior on localhost or HTTPS.
- State left for the next packet: No app state mutation.
