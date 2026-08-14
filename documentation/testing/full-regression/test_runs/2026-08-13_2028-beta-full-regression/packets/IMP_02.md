# Packet: IMP_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: IMP_02.
- In scope: import the five positive GPX files through the README-documented watched folder.
- Out of scope: indexing completion and UI refresh.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_01.
- Required app/data state: empty watched folder and validated staged GPX set.
- Required browser context: desktop context may remain open with stale baseline data.

## Allowed Mutations

- Allowed: copy exactly the five public GPX files into `data/gpx`.
- Not allowed: copy the FIT or synthetic files in this packet; trigger manual rescan before observing the watcher.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| IMP_02 | Copied the five validated public GPX files from staging into the documented watched folder; compared before/after counts and destination checksums. | Five exact positive GPX files enter the watched import path without unrelated files. | Watched-folder count changed from 0 to 5 in 6 ms. All five destination SHA-256 values match the staged public files. | PASS | [assets/IMP_02-copy.txt](../assets/IMP_02-copy.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/IMP_02-copy.txt](../assets/IMP_02-copy.txt) | Watched-folder mutation, timing, filenames, and checksum comparison. |

## Screenshot Evidence

Not applicable; this packet is the filesystem import mutation. UI/index evidence follows.

## Timings

| Step | Timing |
|---|---:|
| Watched-folder copy | 6 ms |

## Handoff Notes

- Completed: exactly five public GPX files are in the watched folder.
- Remaining unfinished coverage: IMP_03 onward and deferred DAT_03 mapping.
- Blocked or not applicable: none.
- State left for the next packet: watcher/indexer may be active; no manual rescan has been triggered.
