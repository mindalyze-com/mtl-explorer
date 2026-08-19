# Packet: NET_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: NET_04
- In scope: Installed web-app service-worker update prompt and accepting the update.
- Out of scope: Normal-tab application-image restart behavior.

## Prerequisites

- Required previous coverage IDs or run packets: NET_03.
- Required app/data state: A registered service worker with a newer waiting version.
- Required browser context: Installed PWA/web-app mode assigned to NET_01-NET_04 by the frozen plan.

## Allowed Mutations

- Allowed: Read-only display-mode and service-worker inspection.
- Not allowed: Replace the required beta image or create an unrelated service worker.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| NET_04 | Inspected installed display mode and active/waiting service-worker registrations after the network recovery. | A new-version prompt appears for a waiting service-worker update and acceptance reloads cleanly. | This is a normal tab, not standalone, with zero registered or waiting service workers. The frozen plan assigns the NET_01-NET_04 offline/cache pass to installed web-app mode, which is absent in this run. | NOT APPLICABLE | [assets/NET_04-update-mode.txt](../assets/NET_04-update-mode.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/NET_04-update-mode.txt](../assets/NET_04-update-mode.txt) | Direct installed-mode, registration, waiting-worker, and plan-scope evidence. |

## Screenshot Evidence

Not useful for a service-worker registration applicability result.

## Timings

| Step | Timing |
|---|---:|
| Applicability inspection | Under 0.1 seconds |

## Handoff Notes

- Completed: Service-worker update applicability was directly checked.
- Remaining unfinished coverage: None for NET_04.
- Blocked or not applicable: NOT APPLICABLE because installed web-app mode and a service worker are absent in this normal-tab run.
- State left for the next packet: Authenticated online desktop map, no network mutation.
