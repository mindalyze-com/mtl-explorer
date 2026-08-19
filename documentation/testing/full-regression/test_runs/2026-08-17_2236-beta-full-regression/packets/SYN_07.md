# Packet: SYN_07

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: SYN_07
- In scope: Live indexer badge and non-blocking map interaction.
- Out of scope: Import correctness of the already-validated public FIT source.

## Prerequisites

- Required previous coverage IDs or run packets: SYN_06.
- Required app/data state: In-sync 15-track map, all jobs idle.
- Required browser context: Desktop Admin Processing over the main map.

## Allowed Mutations

- Allowed: Recoverable temporary copies of the public FIT fixture and cleanup rescan.
- Not allowed: Retain temporary rows/tracks or use private GPX data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SYN_07 | Added a 30-file public-FIT batch, observed live status, zoomed the map, waited terminal, then quarantined and rescanned the batch. | Running indexer is badged but does not block map interaction. | Processing showed `Live`/GPS `SCANNING`; Zoom in changed 500→300 km while Live remained. All jobs reached 49/49, then cleanup left 30 REMOVED rows, zero batch tracks, and 15 intended tracks. | PASS | [assets/SYN_07-live-indexer.txt](../assets/SYN_07-live-indexer.txt); [assets/SYN_07-live-badge.jpg](../assets/SYN_07-live-badge.jpg) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SYN_07-live-indexer.txt](../assets/SYN_07-live-indexer.txt) | Load, live status, zoom result, terminal jobs, and verified cleanup. |
| [assets/SYN_07-live-badge.jpg](../assets/SYN_07-live-badge.jpg) | Live Processing badge/status over the usable map. |

## Screenshot Evidence

- The full viewport preserves the Live navigation badge, GPS scanning progress,
  map controls, and 15-track toolbar at the interaction instant.

## Timings

| Step | Timing |
|---|---:|
| Batch copy to Live badge | About 2 s |
| Zoom interaction | Under 1 s |
| All follow-on jobs terminal | About 125 s |
| Quarantine/rescan cleanup | Under 10 s |

## Handoff Notes

- Completed: Live badge and non-blocking map interaction passed; batch cleanup verified.
- Remaining unfinished coverage: None for SYN_07.
- Blocked or not applicable: None.
- State left for the next packet: Authenticated desktop Admin Maintenance,
  in sync at 15 Tracks; all temporary SYN07 rows are REMOVED.
