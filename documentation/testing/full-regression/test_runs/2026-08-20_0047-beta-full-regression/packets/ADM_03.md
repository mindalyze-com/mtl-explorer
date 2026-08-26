# Packet: ADM_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: ADM_03
- In scope: GPS/media indexer states and manual status refresh.

## Prerequisites

- Required previous coverage IDs or run packets: ADM_02 controlled create/delete cycle.
- Required app/data state: Indexers settled after temporary upload cleanup.
- Required browser context: Admin Processing.

## Allowed Mutations

- Allowed: Refresh status only.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ADM_03 | Compared live ingest/job state with post-cleanup state, then activated Refresh. | GPS/media pending/running/completed/failed/removed state is clear and refresh updates it. | Separate GPS/MEDIA cards exposed active and terminal states/counts; refresh changed GPS from the ingest counts to 14 completed/6 removed/20 total and updated the timestamp. | PASS | [assets/ADM_03-indexers.txt](../assets/ADM_03-indexers.txt) |

## Issues

No new issue.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/ADM_03-indexers.txt](../assets/ADM_03-indexers.txt) | Live/settled GPS, MEDIA, job, and refresh states. |

## Screenshot Evidence

Live desktop inspection confirmed the status cards and state transitions. ACC_04 prevents durable screenshot saving.

## Timings

| Step | Timing |
|---|---:|
| Refresh and render | About 0.75 s |

## Handoff Notes

- Completed: Indexer/card states and refresh update.
- Remaining unfinished coverage: None for ADM_03.
- Blocked or not applicable: Durable screenshots only.
- State left for the next packet: Processing fully settled; 8-track normal data state.
