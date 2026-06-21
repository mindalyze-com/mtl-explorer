# Packet: DEL_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: DEL_02
- In scope: Wait for automatic delete processing or trigger Rescan GPS if needed.
- Out of scope: User-visible deletion verification across app surfaces.

## Prerequisites

- Required previous coverage IDs or run packets: DEL_01.
- Required app/data state: Vitry and VoieVerte source files deleted from watched folder.
- Required browser context: authenticated API/session available.

## Allowed Mutations

- Allowed: Poll read-only status APIs; trigger Rescan GPS only if automatic delete processing does not happen.
- Not allowed: Delete additional files.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DEL_02 | Polled track list, GPS indexer status, jobs, and freshness after deleting two source files. | Automatic delete processing removes the deleted tracks, or Rescan GPS is triggered and recorded. | Automatic processing settled before rescan was needed: API count is 3; deleted filenames are absent; GPS indexer shows completed 3, removed 2, failed 0; jobs pending 0. | PASS | [assets/DEL_02-delete-monitor.txt](../assets/DEL_02-delete-monitor.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DEL_02-delete-monitor.txt](../assets/DEL_02-delete-monitor.txt) | Polling evidence for delete processing and indexer removed count. |

## Screenshot Evidence

No screenshot required for processing monitor.

## Timings

| Step | Timing |
|---|---:|
| Delete processing wait | 0.3 s |

## Handoff Notes

- Completed: DEL_02.
- Remaining unfinished coverage: DEL_03 onward.
- Blocked or not applicable: none.
- State left for the next packet: server source-of-truth has three remaining GPX tracks; client may still need freshness reload.
