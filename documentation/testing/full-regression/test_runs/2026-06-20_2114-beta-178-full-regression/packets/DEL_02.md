# Packet: DEL_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: DEL_02
- In scope: Wait for automatic delete processing or trigger Rescan GPS if needed.
- Out of scope: Surface-level absence checks; covered by DEL_03.

## Prerequisites

- Required previous coverage IDs or run packets: DEL_01.
- Required app/data state: two watched GPX source files deleted from disk.
- Required browser context: authenticated desktop browser.

## Allowed Mutations

- Allowed: poll indexer/jobs/freshness; trigger Rescan GPS only if automatic deletion does not process.
- Not allowed: delete additional files.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DEL_02 | Polled authenticated indexer, job, freshness, and track-count APIs after file deletion. | Automatic delete processing completes, or Rescan GPS is triggered and recorded. | PASS: no manual rescan was needed. GPS indexer shows completed 3, removed 2, pending 0, failed 0; visible track count is 3; background jobs are 3/3 done with pending 0. The GPS `total=5` includes removed entries. | PASS | [assets/DEL_02-delete-processing.txt](../assets/DEL_02-delete-processing.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DEL_02-delete-processing.txt](../assets/DEL_02-delete-processing.txt) | Delete processing state, freshness token, visible count, and jobs status. |

## Screenshot Evidence

No screenshot required; this packet verifies processing state through authenticated status APIs.

## Timings

| Step | Timing |
|---|---:|
| Delete processing poll window | 165 seconds |

## Handoff Notes

- Completed: DEL_02 is terminal.
- Remaining unfinished coverage: DEL_03 onward.
- Blocked or not applicable: none.
- State left for the next packet: visible track count is 3; deleted source files are indexed as removed.
