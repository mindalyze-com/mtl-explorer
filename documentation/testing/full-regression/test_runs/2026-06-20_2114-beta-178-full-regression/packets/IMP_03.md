# Packet: IMP_03

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: IMP_03
- In scope: Wait for indexing to finish after copying five GPX files; record whether manual Rescan GPS was needed.
- Out of scope: UI verification of imported files; covered by IMP_05 through IMP_09.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_02.
- Required app/data state: five GPX files present in watched import folder.
- Required browser context: desktop authenticated browser.

## Allowed Mutations

- Allowed: poll indexer/job APIs; trigger Rescan GPS only if watcher does not react.
- Not allowed: import additional files.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| IMP_03 | Polled authenticated indexer, job, and freshness APIs after the watched-folder copy. | Indexing finishes; if live watching does not react, Rescan GPS is triggered and recorded. | PASS: live watching reacted without manual rescan. First poll showed GPS total/completed 5, pending 0, failed 0, removed 0, progress 100; Duplicate Finder, Activity Classifier, and Exploration Score jobs were all 5/5 done with pending 0. | PASS | [assets/IMP_03-index-wait.txt](../assets/IMP_03-index-wait.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/IMP_03-index-wait.txt](../assets/IMP_03-index-wait.txt) | Poll results for GPS indexer, background jobs, freshness token, and manual rescan decision. |

## Screenshot Evidence

No screenshot required; this packet verifies indexing state through authenticated status APIs.

## Timings

| Step | Timing |
|---|---:|
| Indexer/job settle after first poll | 0 s observed at poll time |

## Handoff Notes

- Completed: IMP_03 is terminal.
- Remaining unfinished coverage: IMP_04 onward; DAT_03 still needs imported IDs after import.
- Blocked or not applicable: none.
- State left for the next packet: five GPX imports are indexed and background jobs are settled.
