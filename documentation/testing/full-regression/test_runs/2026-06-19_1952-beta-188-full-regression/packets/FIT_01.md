# Packet: FIT_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: FIT_01
- In scope: Import the public FIT activity file with GPS positions.
- Out of scope: Conversion/display/download verification.

## Prerequisites

- Required previous coverage IDs or run packets: DEL_05 and DAT_05.
- Required app/data state: three remaining GPX tracks after deletion; public Activity.fit staged outside watched folder.
- Required browser context: none.

## Allowed Mutations

- Allowed: Copy `Activity.fit` into the watched import folder.
- Not allowed: Add other files.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FIT_01 | Copied `Activity.fit` from staged public FIT data into the watched import folder. | FIT activity file is submitted through the import path. | Watched folder contains `Activity.fit` (94,096 bytes) alongside the three remaining GPX files. | PASS | [assets/FIT_01-import-copy.txt](../assets/FIT_01-import-copy.txt); [assets/DAT_05-fit-file.txt](../assets/DAT_05-fit-file.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FIT_01-import-copy.txt](../assets/FIT_01-import-copy.txt) | Watched-folder listing after copying Activity.fit. |
| [assets/DAT_05-fit-file.txt](../assets/DAT_05-fit-file.txt) | Public FIT source URL, size, checksum. |

## Screenshot Evidence

No screenshot required for watched-folder copy.

## Timings

| Step | Timing |
|---|---:|
| Copy FIT file | <1 min |

## Handoff Notes

- Completed: FIT_01.
- Remaining unfinished coverage: FIT_02 onward.
- Blocked or not applicable: none.
- State left for the next packet: Activity.fit present in watched folder, pending indexing/conversion verification.
