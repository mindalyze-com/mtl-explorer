# Packet: SYN_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: SYN_02
- In scope: Freshness Reload updates cached tracks and statistics.
- Out of scope: Banner snooze, covered by SYN_05.

## Prerequisites

- Required previous coverage IDs or run packets: SYN_01.
- Required app/data state: Server has 15 unique tracks; client is stale at 14.
- Required browser context: Freshness banner visible on desktop.

## Allowed Mutations

- Allowed: Click banner Reload, close Admin, and open Statistics.
- Not allowed: Browser restart or hard reload.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SYN_02 | Clicked freshness Reload, verified map count, then opened Stats Overview. | Cached tracks and stats refresh. | Fresh data loaded cleared the banner and changed 14→15 Tracks. Stats showed 15 and listed the 673.67 m MTL Sync Banner Track in Recent Activity. | PASS | [assets/SYN_02-reload.txt](../assets/SYN_02-reload.txt); [assets/SYN_02-refreshed-stats.jpg](../assets/SYN_02-refreshed-stats.jpg) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SYN_02-reload.txt](../assets/SYN_02-reload.txt) | Before/after count, alert, banner state, and full Statistics summary. |
| [assets/SYN_02-refreshed-stats.jpg](../assets/SYN_02-refreshed-stats.jpg) | Refreshed 15-track Statistics view including the new activity. |

## Screenshot Evidence

- The Statistics capture preserves the 15-track summary and refreshed activity
  breakdown after the banner action.

## Timings

| Step | Timing |
|---|---:|
| Freshness Reload | About 2 s |
| Open Statistics | Under 1 s |

## Handoff Notes

- Completed: Banner Reload refreshed both cached track count and Statistics.
- Remaining unfinished coverage: None for SYN_02.
- Blocked or not applicable: None.
- State left for the next packet: Browser/client/server are in sync at 15 tracks;
  Statistics Overview is open.
