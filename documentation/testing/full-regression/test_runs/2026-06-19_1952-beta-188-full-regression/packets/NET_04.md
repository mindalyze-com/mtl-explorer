# Packet: NET_04

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: NET_04
- In scope: Service-worker update prompt applicability.
- Out of scope: Freshness banner cache invalidation, covered by SYN/ADM packets.

## Prerequisites

- Required previous coverage IDs or run packets: NET_03.
- Required app/data state: Browser context where service workers can register and an updated deployed build is available.
- Required browser context: Secure origin or installed PWA/web-app context with service-worker support.

## Allowed Mutations

- Allowed: Inspect runtime service-worker capability and display mode.
- Not allowed: Fake a service-worker update event or deploy a second build.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| NET_04 | Checked runtime service-worker availability and update-prompt applicability on the remote beta target. | A service-worker update event can trigger a new-version prompt, and accepting it reloads cleanly. | The target is a remote plain-HTTP origin: `isSecureContext=false`, `navigator.serviceWorker` unavailable, `display-mode: browser=true`, and no service-worker controller. The update prompt cannot be triggered meaningfully in this run; a second deployed build would also be required. | NOT APPLICABLE | [assets/NET_04-service-worker-update.txt](../assets/NET_04-service-worker-update.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/NET_04-service-worker-update.txt](../assets/NET_04-service-worker-update.txt) | Service-worker update applicability check. |

## Screenshot Evidence

No screenshot evidence is required for this applicability-only packet.

## Timings

| Step | Timing |
|---|---:|
| Service-worker/update context inspection | 3.9 s |

## Handoff Notes

- Completed: NET_04 marked not applicable for this plain-HTTP normal-browser run.
- Remaining unfinished coverage: ERR_01 onward.
- Blocked or not applicable: Service-worker update flow requires a secure/service-worker-capable context plus a second deployed build.
- State left for the next packet: No data mutation.
