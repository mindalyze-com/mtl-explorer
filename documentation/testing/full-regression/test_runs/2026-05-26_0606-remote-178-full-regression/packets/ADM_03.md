# Packet: ADM_03

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: ADM_03
- In scope: Indexer status visibility and refresh behavior.
- Out of scope: Manually forcing every possible runtime state transition.

## Prerequisites

- Required previous coverage IDs or run packets: ADM_02
- Required app/data state: Admin dialog available; ADM_02 upload may have changed indexer/freshness state.
- Required browser context: Desktop browser.

## Allowed Mutations

- Allowed: Open Jobs and press Refresh.
- Not allowed: Run manual rescans for this packet.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ADM_03 | Opened Admin -> Jobs, inspected file indexer and job statuses, then clicked Refresh. | GPS and media indexer states show pending/running/completed/failed/removed status; refresh updates over time. | Jobs panel showed MEDIA and GPS file-indexer progress plus job states. API status exposed pending/completed/failed/removed counts. Refresh updated the timestamp from 10:10:09 to 10:11:06. | PASS | `assets/ADM_03-jobs-panel.webp`, `assets/ADM_03-jobs-refreshed.webp`, `assets/ADM_03-indexer-status.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/ADM_03-jobs-panel.webp | Screenshot of Jobs panel before refresh. |
| assets/ADM_03-jobs-refreshed.webp | Screenshot of Jobs panel after refresh. |
| assets/ADM_03-indexer-status.txt | Compact UI/API status details for indexers and background jobs. |

## Timings

| Step | Timing |
|---|---:|
| Open Jobs, refresh, API status check | <2 min |

## Handoff Notes

- Completed: ADM_03 passed.
- Remaining unfinished coverage: ADM_04 through RUN_CLEANUP.
- Blocked or not applicable: None.
- State left for the next packet: Admin Jobs panel open; freshness banner remains visible.
