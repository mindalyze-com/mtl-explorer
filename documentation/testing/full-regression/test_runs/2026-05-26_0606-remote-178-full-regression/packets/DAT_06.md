# Packet: DAT_06

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: DAT_06
- In scope: public source data validation for required data-change regression.
- Out of scope: importing files into the watched folder unless explicitly covered by IMP/FIT packets.

## Prerequisites

- Required previous coverage IDs or run packets: RUN_SETUP, ACC_01-ACC_05.
- Required app/data state: source files staged outside watched import folder at `/root/mtl-full-regression-2026-05-26_0606/source-data`.
- Required browser context: not required.

## Allowed Mutations

- Allowed: download public test files to staging folder and write evidence.
- Not allowed: copy staged files into watched import folder for DAT-only packets.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DAT_06 | Validated that positive GPX evidence has trackpoint sequences and deferred FIT positive evidence until conversion/display/export packets. | Do not count non-GPS FIT files or waypoint-only GPX as positive evidence. | No waypoint-only GPX was counted; FIT will be counted as positive only after `FIT_02`/`FIT_05` conversion and GPX export evidence. | PASS | `assets/DAT_03-source-manifest.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/DAT_03-source-manifest.txt | Source URLs/notes, destination filenames, checksums, byte sizes, GPX `trkpt` counts, and timestamp counts. |

## Timings

| Step | Timing |
|---|---:|
| Public data download and validation | <1m |

## Handoff Notes

- Completed: DAT_06 status `PASS`.
- Remaining unfinished coverage: continue queue.
- Blocked or not applicable: none unless stated in the row.
- State left for the next packet: source files staged outside watched import folder.
