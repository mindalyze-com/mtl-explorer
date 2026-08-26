# Packet: SYN_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: SYN_01
- In scope: Freshness banner after a server-side re-index change.

## Prerequisites

- Required previous coverage IDs or run packets: ADM_12; client/server initially synchronized.
- Required app/data state: 8-track baseline; ready GPS indexer.
- Required browser context: Signed-in main map.

## Allowed Mutations

- Allowed: Trigger one GPS manual rescan and await normal freshness polling.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SYN_01 | Triggered a GPS rescan from a synchronized map and waited without reload/navigation. | A data-freshness banner appears. | The explicit New data available banner appeared after about 1 s with Reload/Dismiss; map remained usable. | PASS | [assets/SYN_01-freshness-banner.txt](../assets/SYN_01-freshness-banner.txt) |

## Issues

No new issue.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SYN_01-freshness-banner.txt](../assets/SYN_01-freshness-banner.txt) | Before/change/after timing and exact banner content. |

## Screenshot Evidence

Live desktop inspection confirmed the banner over the usable map. ACC_04 prevents durable screenshot saving.

## Timings

| Step | Timing |
|---|---:|
| Banner detection after rescan | About 1.0 s |

## Handoff Notes

- Completed: Server-side re-index to freshness-banner path.
- Remaining unfinished coverage: None for SYN_01.
- Blocked or not applicable: Durable screenshots only.
- State left for the next packet: Freshness banner visible; Reload not yet activated; 8 Tracks.
