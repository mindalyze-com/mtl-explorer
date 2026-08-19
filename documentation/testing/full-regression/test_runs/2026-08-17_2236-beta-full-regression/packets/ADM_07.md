# Packet: ADM_07

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: ADM_07
- In scope: Data freshness last-update/check timestamps and reload action.
- Out of scope: General sync banner policy, covered by SYN_01-SYN_05.

## Prerequisites

- Required previous coverage IDs or run packets: ADM_06.
- Required app/data state: Normal mode restored and synthetic ADM_02 source.
- Required browser context: Desktop Admin > Data status, 14-track map.

## Allowed Mutations

- Allowed: Metadata-touch one synthetic source, manual rescan, and browser reload.
- Not allowed: Change source bytes or restart the browser.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ADM_07 | Recorded in-sync timestamps, changed/reindexed one synthetic source, refreshed Data status, then used freshness-banner Reload. | Last-update timestamp is shown and reload is offered/works. | Latest change advanced 04:37:47→04:39:37; section showed Out of sync/Stale with healthy polling, banner offered Reload, and Reload returned to In sync with a Fresh data loaded alert and 14 Tracks. | PASS | [assets/ADM_07-data-freshness.txt](../assets/ADM_07-data-freshness.txt); [assets/ADM_07-stale.jpg](../assets/ADM_07-stale.jpg) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/ADM_07-data-freshness.txt](../assets/ADM_07-data-freshness.txt) | Baseline, server change, stale timestamps, and reload result. |
| [assets/ADM_07-stale.jpg](../assets/ADM_07-stale.jpg) | Out-of-sync Data status and visible freshness reload banner. |

## Screenshot Evidence

- The clipped Admin panel preserves Out of sync, checked/latest-change times,
  healthy polling, and the global Reload action.

## Timings

| Step | Timing |
|---|---:|
| Rescan to stale revision | About 7 s |
| Data status Refresh | Under 500 ms |
| Freshness Reload | About 2 s |

## Handoff Notes

- Completed: Data freshness timestamp and Reload behavior passed.
- Remaining unfinished coverage: None for ADM_07.
- Blocked or not applicable: None.
- State left for the next packet: Browser is in sync at 14 Tracks; Admin Data
  status remains open; no freshness banner remains.
