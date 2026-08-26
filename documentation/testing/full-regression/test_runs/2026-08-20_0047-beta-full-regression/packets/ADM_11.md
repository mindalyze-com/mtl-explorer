# Packet: ADM_11

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: ADM_11
- In scope: Close/reopen Admin during an active action without losing its state/result.

## Prerequisites

- Required previous coverage IDs or run packets: ADM_10.
- Required app/data state: Healthy MEDIA indexer and settled baseline.
- Required browser context: Admin Maintenance and main map.

## Allowed Mutations

- Allowed: Queue one MEDIA rescan; close/reopen Admin; inspect resulting status.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ADM_11 | Queued MEDIA rescan, immediately closed Admin, reopened it, and inspected Overview/Processing. | Closing/reopening does not lose state mid-action. | The action continued server-side, produced freshness state, and reopened as a coherent done MEDIA result with no duplicate/error. | PASS | [assets/ADM_11-close-reopen.txt](../assets/ADM_11-close-reopen.txt) |

## Issues

No new issue.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/ADM_11-close-reopen.txt](../assets/ADM_11-close-reopen.txt) | Action, close/reopen routes, and settled result. |

## Screenshot Evidence

Live desktop inspection confirmed the reopened state. ACC_04 prevents durable screenshot saving.

## Timings

| Step | Timing |
|---|---:|
| Close-to-reopen Admin | About 0.4 s |
| Reopened status inspection | About 0.25 s |

## Handoff Notes

- Completed: Close/reopen during rescan and final result verification.
- Remaining unfinished coverage: None for ADM_11.
- Blocked or not applicable: Durable screenshots only.
- State left for the next packet: Admin Processing open; rescan complete; freshness banner visible.
