# Packet: MED_09

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_09
- In scope: Visible Admin MEDIA rescan, settlement, exact removed/failure change, freshness change, and visible freshness reload.
- Out of scope: Map-pin absence and database audit details covered by MED_10-12.

## Prerequisites

- Required previous coverage IDs or run packets: MED_08 exact recoverable move.
- Required app/data state: Two watched paths absent; database still at pre-rescan eight-item state.
- Required browser context: Authenticated Admin Maintenance with applied pre-delete freshness token.

## Allowed Mutations

- Allowed: Activate Rescan Media and the resulting freshness Reload action.
- Not allowed: API-trigger rescan, restore files, or mutate other data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_09 | Recorded pre-state, activated Admin Rescan Media, waited for MEDIA to settle, compared counters/token, observed Stale/New data available, and activated its Reload. | Removed rises by two, failures do not rise, freshness changes, and the GUI reload applies fresh data. | MEDIA changed completed 8->6 and removed 5->7 with pending/failed 0; exact delete rows were processed, index/media revisions changed, and visible Reload confirmed `Map updated — Fresh data loaded`. | PASS | [assets/MED_09-rescan-freshness.txt](../assets/MED_09-rescan-freshness.txt) |

## Issues

No new issue.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_09-rescan-freshness.txt](../assets/MED_09-rescan-freshness.txt) | Before/after MEDIA counters, exact deletion logs, freshness revisions/token, and visible apply result. |

## Screenshot Evidence

Live desktop inspection confirmed queued, Stale/New data available, and Map updated states. ACC_04 prevents durable screenshot saving.

## Timings

| Step | Timing |
|---|---:|
| Server MEDIA rescan | 82 ms |
| UI freshness notification | About 5.6 s |
| Freshness apply settlement | About 1.7 s |

## Handoff Notes

- Completed: GUI rescan, exact counter/failure checks, freshness transition, and GUI reload.
- Remaining unfinished coverage: None for MED_09.
- Blocked or not applicable: Durable screenshots remain blocked by ACC_04.
- State left for the next packet: Browser has applied post-delete token; active media/resolved/selected 6/6/6; queues 0/0; backups retained outside watched tree.
