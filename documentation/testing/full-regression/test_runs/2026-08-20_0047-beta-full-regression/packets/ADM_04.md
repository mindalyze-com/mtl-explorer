# Packet: ADM_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: ADM_04
- In scope: GPS/media manual rescans, queued/already-running/not-ready outcomes, and map interaction.

## Prerequisites

- Required previous coverage IDs or run packets: ADM_03.
- Required app/data state: Healthy ready GPS and MEDIA indexers.
- Required browser context: Admin Maintenance and authenticated map.

## Allowed Mutations

- Allowed: Queue bounded GPS and media rescans; exercise concurrent idempotence; use one map zoom action.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ADM_04 | Queued GPS and MEDIA rescans, exercised concurrent GPS requests, and used/reopened the map during work. | Queued/already-running/not-ready states are clear and map interaction remains usable. | Both UI actions reported queued; concurrent GPS requests returned one STARTED and seven clear ALREADY_RUNNING results; map zoom/navigation remained usable. Not-ready was inapplicable because indexers were healthy. | PASS | [assets/ADM_04-rescans.txt](../assets/ADM_04-rescans.txt) |

## Issues

No new issue.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/ADM_04-rescans.txt](../assets/ADM_04-rescans.txt) | UI messages, concurrent outcome matrix, and map interaction. |

## Screenshot Evidence

Live desktop inspection confirmed rescan messages and continued map use. ACC_04 prevents durable screenshot saving.

## Timings

| Step | Timing |
|---|---:|
| UI queue acknowledgements | About 0.15 s each |
| Map close/zoom/reopen flow | About 7 s |

## Handoff Notes

- Completed: GPS/media queue, already-running response, map non-blocking behavior.
- Remaining unfinished coverage: None for ADM_04.
- Blocked or not applicable: Not-ready substate is not applicable while both indexers are initialized and healthy; screenshots blocked.
- State left for the next packet: Rescans queued/completing; freshness banner visible; map still at 8 Tracks.
