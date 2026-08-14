# Packet: DEL_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: DEL_02.
- In scope: wait for automatic deletion processing or trigger Rescan GPS if needed.
- Out of scope: cross-surface disappearance assertions, covered by DEL_03-DEL_05.

## Prerequisites

- Required previous coverage IDs or run packets: DEL_01.
- Required app/data state: exactly two source files removed from the watched folder.
- Required browser context: signed-in map left open to observe freshness.

## Allowed Mutations

- Allowed: wait; use Rescan GPS only if live watching does not react.
- Not allowed: delete more files or reload UI data before processing is observed.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DEL_02 | Waited for the live watcher, checked application logs, and inspected browser freshness without starting a rescan. | Both deleted sources are processed automatically, or Rescan GPS is used and recorded. | IDs 100001 and 100003 were deleted automatically 8.308-8.309 s after the file mutation. The browser showed the New data available notice. Rescan GPS was not needed. | PASS | [assets/DEL_02-processing.txt](../assets/DEL_02-processing.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DEL_02-processing.txt](../assets/DEL_02-processing.txt) | Server delete completion timestamps and browser freshness observation. |

## Screenshot Evidence

The visible refresh and post-delete state are captured in DEL_03.

## Timings

| Step | Timing |
|---|---:|
| Automatic two-record delete processing | 8.309 s |

## Handoff Notes

- Completed: automatic watcher deletion for both records; no rescan required.
- Remaining unfinished coverage: DEL_03 onward; DAT_03 still needs the FIT imported mapping.
- Blocked or not applicable: none.
- State left for the next packet: stale five-track browser snapshot with New data available notice; server state has three tracks.
