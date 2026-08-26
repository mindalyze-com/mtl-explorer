# Packet: IMP_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: IMP_02
- In scope: Import five GPX files through the documented watched import folder.
- Out of scope: Waiting for index completion.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_01 baseline; DAT_01-DAT_04 fixtures.
- Required app/data state: GPS indexer idle 0/0 and empty watched folder.
- Required browser context: Admin remains available for follow-up.

## Allowed Mutations

- Allowed: Create the run-specific subfolder under `data/gpx` and copy the five public files.
- Not allowed: Copy any private local track or extra positive file in this step.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| IMP_02 | Copy all five staged public GPX files into `data/gpx/<run-id>/`; re-check size and SHA-256. | Five source files enter the documented watched folder unchanged. | Five files were copied at 23:13:14Z and every watched copy matches its staged checksum. | PASS | [assets/IMP_02-copy.txt](../assets/IMP_02-copy.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/IMP_02-copy.txt](../assets/IMP_02-copy.txt) | Import method, destination, timestamp, size, and checksum evidence. |

## Screenshot Evidence

Not useful for the filesystem copy; Admin indexing evidence follows in IMP_03/IMP_04.

## Timings

| Step | Timing |
|---|---:|
| Five-file copy/checksum | <1 s |

## Handoff Notes

- Completed: Exact public GPX files placed in the documented watched folder.
- Remaining unfinished coverage: None for IMP_02.
- Blocked or not applicable: None.
- State left for the next packet: GPS live watcher is expected to process five new files.
