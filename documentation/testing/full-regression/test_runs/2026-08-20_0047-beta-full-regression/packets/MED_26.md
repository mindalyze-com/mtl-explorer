# Packet: MED_26

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_26
- In scope: Deterministic batch failure, per-item retry isolation, defer metadata, healthy follower completion, and no tight retry.
- Out of scope: User-facing queue diagnostics.

## Prerequisites

- Required previous coverage IDs or run packets: MED_25 cleanup.
- Required app/data state: Empty correlation queues and disposable PostGIS access.
- Required browser context: None; this is a worker/database isolation packet.

## Allowed Mutations

- Allowed: Two synthetic media rows and one deliberately malformed synthetic route geometry with valid timed points.
- Not allowed: Corrupt an original track/media row or retain the deferred work item.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_26 | Queue a deterministic PostGIS failure as media 2600000 immediately before healthy media 2600001; inspect logs, work metadata, healthy projections, and later retry count; clean fixture. | Failure is deferred with attempt/error metadata; healthy follower completes; blocked batch is not retried forever. | Batch split into individual items. Failure got attempt 1, exact error, and 300-second defer. Healthy item completed correlation/resolution in the same run. After ~101 s, attempt stayed 1 and only one defer log existed. Cleanup exact. | PASS | [assets/MED_26-failure-isolation.txt](../assets/MED_26-failure-isolation.txt); [assets/MED_26-seed.sql](../assets/MED_26-seed.sql); [assets/MED_26-cleanup.sql](../assets/MED_26-cleanup.sql) |

## Issues

None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_26-failure-isolation.txt](../assets/MED_26-failure-isolation.txt) | Queue ordering, exact error/defer metadata, healthy completion, retry observation, and cleanup. |
| [assets/MED_26-seed.sql](../assets/MED_26-seed.sql) | Deterministic malformed-route and healthy-follower fixture. |
| [assets/MED_26-cleanup.sql](../assets/MED_26-cleanup.sql) | Bounded removal of every fixture row. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; exact worker logs and database evidence are linked above.

## Timings

| Step | Timing |
|---|---:|
| Batch split, defer, and healthy completion | 90 ms worker run |
| No-tight-retry observation | ~101 s |

## Handoff Notes

- Completed: Deterministic failure, individual isolation, defer metadata, healthy progress, retry suppression, and cleanup.
- Remaining unfinished coverage: None for MED_26.
- Blocked or not applicable: Screenshot evidence remains blocked by ACC_04 and is not material to this worker packet.
- State left for the next packet: No MED_26 rows; original 8/8/8 media baseline; both correlation queues empty.
