# Packet: NET_04

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: NET_04
- In scope: Service-worker update prompt and clean reload behavior.
- Out of scope: Other coverage IDs except where noted as shared state/evidence.

## Prerequisites

- Required previous coverage IDs or run packets: Previous queue rows terminal or explicitly not required.
- Required app/data state: Current run-state and packet set for this run.
- Required browser context: As specified by the coverage action.

## Allowed Mutations

- Allowed: Read-only verification and packet/run-state updates.
- Not allowed: Collapse this ID into a parent row or skip required direct evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| NET_04 | Checked the deployed PWA assets, local update-registration code, and browser runtime service-worker availability on the target origin. | When a service worker is active and a newer deployed build is available, MTL Explorer should surface a new-version update path and reload cleanly after acceptance. | The target run uses remote plain HTTP. Runtime evidence shows isSecureContext=false and navigator.serviceWorker unavailable, so no service worker can register or receive an update in this browser context. The deployed manifest and sw.js are served, but the update path also requires a second deployed build/version change. | NOT APPLICABLE | [NET_04-service-worker-runtime.txt](../assets/NET_04-service-worker-runtime.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [NET_04-service-worker-runtime.txt](../assets/NET_04-service-worker-runtime.txt) | Text/log evidence |

## Screenshot Evidence

No screenshot evidence for this packet.

## Timings

| Step | Timing |
|---|---:|
| Runtime SW applicability check | 3 seconds |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
