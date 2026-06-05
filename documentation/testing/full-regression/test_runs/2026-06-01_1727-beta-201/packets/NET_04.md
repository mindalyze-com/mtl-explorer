# Packet: NET_04

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: NET_04
- In scope: Determine whether a service-worker update prompt event is applicable in this fixed-target run.
- Out of scope: Deploying a new client build during the regression run.

## Prerequisites

- Required previous coverage IDs or run packets: NET_03.
- Required app/data state: Browser can load the app.
- Required browser context: Current remote plain-HTTP regression browser context.

## Allowed Mutations

- Allowed: Inspect service-worker support and update registration state.
- Not allowed: Deploy a new app version or force a synthetic update prompt.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| NET_04 | Checked service-worker support/registrations, attempted registration update inspection if available, and searched for update-prompt UI. | A new-version prompt appears after an update and accepting it reloads cleanly. | The remote plain-HTTP browser context reported no service-worker support/registrations, and no new client build was deployed during this fixed beta-target run. No update prompt event was applicable. | NOT APPLICABLE | [assets/NET_04-service-worker-update.txt](../assets/NET_04-service-worker-update.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/NET_04-service-worker-update.txt](../assets/NET_04-service-worker-update.txt) | Service-worker support, update applicability, and no-prompt evidence. |

## Timings

| Step | Timing |
|---|---:|
| Service-worker update applicability check | ~1 min |

## Handoff Notes

- Completed: NET_04 terminal as `NOT APPLICABLE`.
- Remaining unfinished coverage: Continue with ERR_01.
- Blocked or not applicable: Service-worker update prompt requires service-worker support and a deployed new client version; neither applied in this run.
- State left for the next packet: No app/server state changed.
