# Packet: IMP_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: IMP_03
- In scope: Wait for indexing; use Rescan GPS only if live watching fails.
- Out of scope: Background job completion and freshness reload.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_02.
- Required app/data state: Exactly five new GPX files in the watched run subfolder.
- Required browser context: Admin Processing.

## Allowed Mutations

- Allowed: Refresh processing status; trigger Rescan GPS only if needed.
- Not allowed: Rescan unnecessarily or add more files.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| IMP_03 | Waited for the live watcher, opened Admin Processing, and refreshed the status without invoking Rescan GPS. | All five files index automatically, or a recorded manual rescan is used only if watching does not react. | GPS reached `done` with 5 completed, 0 failures shown, and 1 excluded directory entry out of 6 filesystem entries. No rescan was needed. | PASS | [assets/IMP_03-indexing.txt](../assets/IMP_03-indexing.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/IMP_03-indexing.txt](../assets/IMP_03-indexing.txt) | Direct Admin Processing and map-count observations. |

## Screenshot Evidence

Blocked by ACC_04; direct DOM status evidence is recorded.

## Timings

| Step | Timing |
|---|---:|
| Copy to first confirmed GPS done status | Under 20 s |

## Handoff Notes

- Completed: Five files indexed through live watching; no manual rescan.
- Remaining unfinished coverage: None for IMP_03.
- Blocked or not applicable: None.
- State left for the next packet: GPS done; follow-on background jobs still settling.
