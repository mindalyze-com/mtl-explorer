# Packet: NET_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: NET_02
- In scope: Recoverable UI during a flaky connection.

## Prerequisites

- Required previous coverage IDs or run packets: NET_01.
- Required app/data state: Healthy disposable stack and warmed signed-in app.
- Required browser context: Statistics reachable from the loaded shell.

## Allowed Mutations

- Allowed: Briefly stop and restart only the disposable app container.
- Not allowed: Interrupt database/storage or leave the service stopped.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| NET_02 | Stopped the app, opened Statistics, restarted it, used Retry, then used the later Reload recovery action. Retested with a separately stoppable local proxy while keeping the loaded page and backend running. | Failure is actionable and recovers without a blank/frozen shell. | Fixed locally: the outage shows both Statistics errors, then one Retry after proxy restoration clears both and restores the overview at desktop and 390x760 sizes. | FIXED | [assets/NET_02-flaky-recovery.txt](../assets/NET_02-flaky-recovery.txt); [assets/MTL-FR-023-fix-local.txt](../assets/MTL-FR-023-fix-local.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Finding status | Release impact |
|---|---|---|---|---|---|---|---|---|
| MTL-FR-023 | P2 | Statistics Retry does not recover after connectivity returns. | Open Statistics while the app endpoint is unavailable; restore it; activate Retry. | Statistics refetches and renders, or Retry remains available with an actionable error. | Fixed locally: one Retry refreshes both the parent statistics request and the independently owned overview request, removes both errors, and restores data. | [assets/MTL-FR-023-fix-local.txt](../assets/MTL-FR-023-fix-local.txt) | FIXED | No remaining release impact in the verified local flow. |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/NET_02-flaky-recovery.txt](../assets/NET_02-flaky-recovery.txt) | Controlled outage, error UI, failed Retry, successful Reload, and service restoration. |
| [assets/MTL-FR-023-fix-local.txt](../assets/MTL-FR-023-fix-local.txt) | Root cause, implementation, automated checks, and current-worktree recovery retest. |

## Screenshot Evidence

| View | Evidence |
|---|---|
| Desktop recovered after one Retry | [assets/MTL-FR-023-fix-local-desktop.webp](../assets/MTL-FR-023-fix-local-desktop.webp) |
| Mobile 390x760 recovered after one Retry | [assets/MTL-FR-023-fix-local-mobile.webp](../assets/MTL-FR-023-fix-local-mobile.webp) |

## Timings

| Step | Timing |
|---|---:|
| Outage click to error state | About 6.2 s |
| Service return to HTTP 200 | About 14 s |
| Retry observation | More than 7 s |
| Reload to recovered Stats | About 2.8 s |

## Handoff Notes

- Completed: Controlled connection-loss and recovery flow, plus the fixed single-Retry retest at desktop and mobile sizes.
- Remaining unfinished coverage: None for NET_02.
- Blocked or not applicable: None for the fixed Statistics Retry path.
- State left for the next packet: App running; Statistics loaded; eight tracks; de-DE/Metric.

## Fix Record

- Root cause: the top Retry reloaded parent trend requests but did not signal the independently loaded Statistics Overview.
- Source: `mtl-client/src/components/statistics/Statistics.vue` and `mtl-client/src/components/statistics/StatisticsOverview.vue`.
- Tests: focused retry/recovery coverage passed; the combined client suite passed 762 tests with production build and type-check.
- UI proof: a separately stoppable local proxy produced both errors; after recovery, one Retry restored the overview at desktop and 390x760 sizes without page reload.
