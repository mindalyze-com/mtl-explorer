# Packet: SYN_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: SYN_04
- In scope: FIT import freshness and cache propagation parity with GPX.
- Out of scope: Download artifact access.

## Prerequisites

- Required previous coverage IDs or run packets: FIT_01, FIT_02, SYN_03.
- Required app/data state: Original FIT-backed track 100005 retained.
- Required browser context: Desktop Statistics Tracks and Track Details.

## Allowed Mutations

- Allowed: Reconcile durable import evidence and reopen retained FIT details.
- Not allowed: Re-import the same FIT or substitute pre-converted GPX.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SYN_04 | Reconciled the original FIT mutation/freshness reload and directly rechecked its current Statistics row/details. | FIT conversion changes freshness/cache state like native GPX. | Original FIT produced the same banner/reload flow and 5→6 map change; current cache still exposes the FIT-backed 100005 row and full details. | PASS | [assets/SYN_04-fit-freshness-audit.txt](../assets/SYN_04-fit-freshness-audit.txt); [assets/FIT_01-copy.txt](../assets/FIT_01-copy.txt); [assets/FIT_02-index-display.txt](../assets/FIT_02-index-display.txt); [assets/SYN_04-fit-details.jpg](../assets/SYN_04-fit-details.jpg) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SYN_04-fit-freshness-audit.txt](../assets/SYN_04-fit-freshness-audit.txt) | Original parity flow and direct current row/detail audit. |
| [assets/FIT_01-copy.txt](../assets/FIT_01-copy.txt) | Original unchanged FIT checksum and watcher mutation. |
| [assets/FIT_02-index-display.txt](../assets/FIT_02-index-display.txt) | Indexer, banner, reload, map, search, stats, and ID evidence. |
| [assets/SYN_04-fit-details.jpg](../assets/SYN_04-fit-details.jpg) | Current FIT-backed track 100005 details. |

## Screenshot Evidence

- The current details capture preserves track 100005, Activity.fit identity,
  Walking classification, and non-zero distance/duration/elevation metrics.

## Timings

| Step | Timing |
|---|---:|
| Original FIT index/reload | Under 35 s |
| Current read-only audit | About 2 s |

## Handoff Notes

- Completed: FIT uses the same freshness/cache propagation as native GPX.
- Remaining unfinished coverage: None for SYN_04.
- Blocked or not applicable: None.
- State left for the next packet: FIT track 100005 details are open; client is
  in sync at 15 tracks.
