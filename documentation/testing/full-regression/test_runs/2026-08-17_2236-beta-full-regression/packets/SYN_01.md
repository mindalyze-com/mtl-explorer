# Packet: SYN_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: SYN_01
- In scope: Freshness banner after a new server-side import.
- Out of scope: Applying the banner reload, covered by SYN_02.

## Prerequisites

- Required previous coverage IDs or run packets: ADM_12.
- Required app/data state: In-sync 14-track client and healthy indexer.
- Required browser context: Desktop Admin Import & sync.

## Allowed Mutations

- Allowed: Upload one small synthetic GPX and let normal indexing finish.
- Not allowed: Reload or dismiss the resulting banner in this packet.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SYN_01 | Uploaded a unique six-point GPX, verified successful indexing, and waited through freshness polling. | A data-freshness banner appears after the server-side change. | Track 100026 indexed successfully; `New data available` with Reload/Dismiss appeared in about 7 s while the cached toolbar stayed at 14 Tracks. | PASS | [assets/SYN_01-freshness.txt](../assets/SYN_01-freshness.txt); [assets/SYN_01-freshness-banner.jpg](../assets/SYN_01-freshness-banner.jpg); [assets/SYN_01-new-track.gpx](../assets/SYN_01-new-track.gpx) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SYN_01-freshness.txt](../assets/SYN_01-freshness.txt) | Baseline, upload/index result, poll timing, and stale client count. |
| [assets/SYN_01-freshness-banner.jpg](../assets/SYN_01-freshness-banner.jpg) | Successful import plus visible freshness banner and 14-track cached state. |
| [assets/SYN_01-new-track.gpx](../assets/SYN_01-new-track.gpx) | Fully synthetic source used for the unique import. |

## Screenshot Evidence

- The full viewport preserves the successful upload, stale 14-track client, and
  banner with Reload and Dismiss.

## Timings

| Step | Timing |
|---|---:|
| Upload | Under 1 s |
| Index and freshness banner | About 7 s |

## Handoff Notes

- Completed: A real unique GPX import triggered the freshness banner.
- Remaining unfinished coverage: None for SYN_01.
- Blocked or not applicable: None.
- State left for the next packet: Track 100026 exists server-side; client still
  shows 14 Tracks with the freshness banner awaiting Reload.
