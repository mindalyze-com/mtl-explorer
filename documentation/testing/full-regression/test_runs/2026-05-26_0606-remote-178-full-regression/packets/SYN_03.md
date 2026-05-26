# Packet: SYN_03

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: SYN_03
- In scope: Required five-GPX import and delete-two-track synchronization flow.
- Out of scope: Re-running the destructive import/delete sequence after it has already passed.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_02 through IMP_09, DEL_01 through DEL_05, SYN_02
- Required app/data state: Completed import/delete packet evidence exists in this run.
- Required browser context: Not required for this aggregate packet.

## Allowed Mutations

- Allowed: Review durable packet evidence.
- Not allowed: Recreate or delete additional source GPX files for this aggregate check.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SYN_03 | Audited completed IMP/DEL packets for the required five-GPX import and delete-two sequence. | Indexer state, freshness banner, map, browser, stats, filters, heatmap, and details all reflect source-of-truth file changes. | Earlier packets directly verified import success, freshness reload, map/browser/stats/filter/heatmap/detail updates, automatic delete processing, and removal of deleted tracks from user-visible surfaces. | PASS | `assets/SYN_03-five-gpx-delete-flow.txt`, `packets/IMP_02.md` through `packets/IMP_09.md`, `packets/DEL_01.md` through `packets/DEL_05.md` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/SYN_03-five-gpx-delete-flow.txt | Compact index of durable packet evidence for the required flow. |

## Timings

| Step | Timing |
|---|---:|
| Packet evidence audit | <1 min |

## Handoff Notes

- Completed: SYN_03 passed.
- Remaining unfinished coverage: SYN_04 through RUN_CLEANUP.
- Blocked or not applicable: None.
- State left for the next packet: No app state mutation.
