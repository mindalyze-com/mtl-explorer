# Packet: FIT_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: FIT_01
- In scope: Import the public GPS-bearing FIT activity file.
- Out of scope: Index/display/download verification; covered by FIT_02+.

## Prerequisites

- Required previous coverage IDs or run packets: DEL_05, DAT_05.
- Required app/data state: FIT sample staged in `/root/mtl-regression-2026-06-01_1727-beta-201/test-inputs/Activity.fit`.
- Required browser context: None.

## Allowed Mutations

- Allowed: Copy `Activity.fit` into watched import folder.
- Not allowed: Reimport deleted GPX files.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FIT_01 | Copied Garmin FIT SDK `Activity.fit` into the watched import folder. | FIT file is present in watched folder for import/indexing. | `Activity.fit` was copied to `/root/mtl-regression-2026-06-01_1727-beta-201/mtl-explorer/data/gpx/Activity.fit`; checksum matches DAT manifest (`949a238e...`). | PASS | [assets/FIT_01-copy-fit-file.txt](../assets/FIT_01-copy-fit-file.txt), [assets/DAT-public-data-manifest.json](../assets/DAT-public-data-manifest.json) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FIT_01-copy-fit-file.txt](../assets/FIT_01-copy-fit-file.txt) | FIT copy output, watched-folder listing, and checksum. |
| [assets/DAT-public-data-manifest.json](../assets/DAT-public-data-manifest.json) | Expected FIT source/checksum metadata. |

## Timings

| Step | Timing |
|---|---:|
| FIT watched-folder copy | <1 second |

## Handoff Notes

- Completed: FIT_01 terminal as `PASS`.
- Remaining unfinished coverage: Continue with `FIT_02` acceptance/index/display/statistics verification.
- Blocked or not applicable: None.
- State left for the next packet: `Activity.fit` is present in watched folder; indexing may still be processing.
