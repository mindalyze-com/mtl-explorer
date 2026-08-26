# Packet: ADM_07

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: ADM_07
- In scope: Admin data-freshness timestamp, stale/current detail, and reload.

## Prerequisites

- Required previous coverage IDs or run packets: ADM_04 rescan revision change.
- Required app/data state: Server index revision newer than the browser's applied revision.
- Required browser context: Admin Data status with global freshness banner.

## Allowed Mutations

- Allowed: Apply the visible Reload action.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ADM_07 | Inspected stale timestamps/tokens, then activated the freshness banner's Reload. | Last-update time is visible and reload is offered. | Checked/latest-change timestamps, stale index revisions, healthy polling, Reload/Dismiss, and exact post-reload synchronization were all explicit. | PASS | [assets/ADM_07-data-freshness.txt](../assets/ADM_07-data-freshness.txt) |

## Issues

No new issue.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/ADM_07-data-freshness.txt](../assets/ADM_07-data-freshness.txt) | Exact stale/current timestamps, tokens, and banner action. |

## Screenshot Evidence

Live desktop inspection confirmed both states. ACC_04 prevents durable screenshot saving.

## Timings

| Step | Timing |
|---|---:|
| Reload and synchronization | About 0.8 s |

## Handoff Notes

- Completed: Timestamp, stale detail, reload offer, and synchronization.
- Remaining unfinished coverage: None for ADM_07.
- Blocked or not applicable: Durable screenshots only.
- State left for the next packet: Data status in sync at index r100511.
