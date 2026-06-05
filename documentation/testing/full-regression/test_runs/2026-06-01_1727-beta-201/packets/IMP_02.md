# Packet: IMP_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: IMP_02
- In scope: Import the five GPX files through the documented watched import folder.
- Out of scope: Waiting for indexing and UI verification; those are covered by IMP_03+.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_01.
- Required app/data state: Public GPX files staged in `/root/mtl-regression-2026-06-01_1727-beta-201/test-inputs/`; watched folder empty before copy.
- Required browser context: None.

## Allowed Mutations

- Allowed: Copy the five GPX public samples into `/root/mtl-regression-2026-06-01_1727-beta-201/mtl-explorer/data/gpx/`.
- Not allowed: Copy FIT sample yet; delete imported files.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| IMP_02 | Copied the five staged public GPX files into the README-documented watched import folder `./data/gpx/` under the quick-install directory. | All five GPX files are present in watched folder with checksums matching the DAT manifest. | Five GPX files were copied to `/root/mtl-regression-2026-06-01_1727-beta-201/mtl-explorer/data/gpx/`; watched-folder byte sizes and SHA-256 values match the public data manifest. | PASS | [assets/IMP_02-copy-gpx-files.txt](../assets/IMP_02-copy-gpx-files.txt), [assets/DAT-public-data-manifest.json](../assets/DAT-public-data-manifest.json) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/IMP_02-copy-gpx-files.txt](../assets/IMP_02-copy-gpx-files.txt) | Copy command output, watched-folder listing, and checksums. |
| [assets/DAT-public-data-manifest.json](../assets/DAT-public-data-manifest.json) | Expected public sample checksums and metadata. |

## Timings

| Step | Timing |
|---|---:|
| Watched-folder copy | <1 second |

## Handoff Notes

- Completed: IMP_02 terminal as `PASS`.
- Remaining unfinished coverage: Continue with `IMP_03` and wait for indexing to finish.
- Blocked or not applicable: None.
- State left for the next packet: Five GPX files are present in the watched import folder; FIT sample remains staged but not imported.
