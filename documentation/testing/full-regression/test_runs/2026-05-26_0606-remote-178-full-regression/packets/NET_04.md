# Packet: NET_04

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: NET_04
- In scope: Determine whether service-worker update prompt scope applies.
- Out of scope: Deploying a new app/service-worker version.

## Prerequisites

- Required previous coverage IDs or run packets: NET_03
- Required app/data state: A new client build/service-worker version is deployed during the test.
- Required browser context: Browser with existing service worker registration.

## Allowed Mutations

- Allowed: Inspect applicability.
- Not allowed: Rebuild or redeploy the target outside the configured quick-install retest.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| NET_04 | Checked whether the run included a service-worker/client update event. | A new-version prompt appears after an update and accepting it reloads cleanly. | No new client build or service-worker version was deployed during this fixed-target regression run, so no update prompt event was applicable. | NOT APPLICABLE | `assets/NET_04-service-worker-update.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/NET_04-service-worker-update.txt | Service-worker update applicability decision. |

## Timings

| Step | Timing |
|---|---:|
| Applicability check | <1 min |

## Handoff Notes

- Completed: NET_04 terminal `NOT APPLICABLE`.
- Remaining unfinished coverage: ERR_01 through RUN_CLEANUP.
- Blocked or not applicable: No service-worker update event occurred in this fixed-target run.
- State left for the next packet: Main browser remains signed in on desktop viewport.
