# Packet: SYN_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: SYN_02
- In scope: Freshness-banner reload refreshes cached tracks and statistics.

## Prerequisites

- Required previous coverage IDs or run packets: SYN_01 with visible banner.
- Required app/data state: Server index revision newer than client; underlying track source unchanged.
- Required browser context: Signed-in main map.

## Allowed Mutations

- Allowed: Activate banner Reload; open Statistics Overview and Tracks.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SYN_02 | Activated banner Reload, then checked map, Statistics Overview, and Statistics Tracks. | Cached tracks and stats refresh to server source of truth. | Banner cleared; map/Overview/Tracks consistently showed 8 tracks and matching populated totals/table with no stale fixture. | PASS | [assets/SYN_02-banner-reload.txt](../assets/SYN_02-banner-reload.txt) |

## Issues

No new issue.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SYN_02-banner-reload.txt](../assets/SYN_02-banner-reload.txt) | Reload settlement and cross-surface cache values. |

## Screenshot Evidence

Live desktop inspection confirmed refreshed map/statistics state. ACC_04 prevents durable screenshot saving.

## Timings

| Step | Timing |
|---|---:|
| Banner reload | About 0.85 s |
| Each Statistics tab render | About 0.3 s |

## Handoff Notes

- Completed: Banner reload and map/Overview/Tracks cache verification.
- Remaining unfinished coverage: None for SYN_02.
- Blocked or not applicable: Durable screenshots only.
- State left for the next packet: Statistics Tracks open; synchronized 8-track state.
