# Packet: IMP_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: IMP_02
- In scope: Import the five public GPX files through the documented watched import folder.
- Out of scope: Index completion and UI verification; covered by IMP_03 onward.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_01.
- Required app/data state: five public GPX files staged in source-data; watched import folder empty of these files.
- Required browser context: none.

## Allowed Mutations

- Allowed: copy the five selected GPX files into `data/gpx`.
- Not allowed: copy FIT, synthetic, or private files for this packet.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| IMP_02 | Copied the five selected public GPX files from source-data into the documented watched import folder. | Five GPX files are placed into the watched import folder with source checksums preserved. | PASS: all five files are present in `data/gpx` with SHA-256 hashes matching the staged public downloads; FIT and synthetic files were intentionally not copied for this packet. | PASS | [assets/IMP_02-import-copy.txt](../assets/IMP_02-import-copy.txt); [assets/DAT_public-data-metadata.txt](../assets/DAT_public-data-metadata.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/IMP_02-import-copy.txt](../assets/IMP_02-import-copy.txt) | Before/after import folder listing and destination checksums. |
| [assets/DAT_public-data-metadata.txt](../assets/DAT_public-data-metadata.txt) | Source checksums for comparison. |

## Screenshot Evidence

Not applicable; this is a watched-folder file mutation.

## Timings

| Step | Timing |
|---|---:|
| Five-GPX watched-folder copy and checksum verification | <1 minute |

## Handoff Notes

- Completed: IMP_02 is terminal.
- Remaining unfinished coverage: IMP_03 onward; DAT_03 still needs imported IDs after import.
- Blocked or not applicable: none.
- State left for the next packet: five GPX files are now in the watched import folder and the server should index them automatically.
