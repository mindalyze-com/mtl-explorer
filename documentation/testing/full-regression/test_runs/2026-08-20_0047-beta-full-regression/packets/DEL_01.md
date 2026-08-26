# Packet: DEL_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: DEL_01
- In scope: Remove exactly two imported source files from the watched folder.
- Out of scope: Indexer and frontend results, covered by DEL_02-DEL_05.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_08 readiness checkpoint.
- Required app/data state: Full five-GPX set no longer needed by later checks.
- Required browser context: None for the filesystem mutation.

## Allowed Mutations

- Allowed: Move exactly two selected public GPX copies to a recoverable quarantine outside the watched tree.
- Not allowed: Move any other source or touch private/local data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DEL_01 | Move Vitry and Voie Verte GPX copies out of the watched run folder. | Exactly two imported sources leave the watched folder. | Exactly the two intended public files moved to a recoverable disposable quarantine; five intended files remained. | PASS | [assets/DEL_01-two-file-removal.txt](../assets/DEL_01-two-file-removal.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DEL_01-two-file-removal.txt](../assets/DEL_01-two-file-removal.txt) | Exact removed, recovery, and retained file paths. |

## Screenshot Evidence

Not applicable to the filesystem mutation.

## Timings

| Step | Timing |
|---|---:|
| Resolve exact paths and move two files | 1 min |

## Handoff Notes

- Completed: Exactly two watched public GPX files removed recoverably.
- Remaining unfinished coverage: None for DEL_01.
- Blocked or not applicable: None.
- State left for the next packet: Watcher processing the two delete events.
