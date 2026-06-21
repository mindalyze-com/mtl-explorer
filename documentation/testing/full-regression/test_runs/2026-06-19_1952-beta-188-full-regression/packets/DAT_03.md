# Packet: DAT_03

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md` required data-change section.
- Coverage ID or run packet: DAT_03
- In scope: Source metadata for every staged source file, with imported IDs/names to be appended after import.
- Out of scope: Importing staged files into the watched folder unless covered by IMP/FIT/FMT packets.

## Prerequisites

- Required previous coverage IDs or run packets: preceding DAT packets.
- Required app/data state: source files staged under `/root/mtl-full-regression-2026-06-19_1952-beta-188-full-regression/source-data`, outside the watched import folder.
- Required browser context: none.

## Allowed Mutations

- Allowed: stage public or synthetic test files outside watched import folder; update this packet and run-state.
- Not allowed: copy staged files into `data/gpx` for DAT-only packets.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DAT_03 | Recorded source URL/note, destination filename, SHA-256, byte size, GPX trkpt count, timestamp count, source track names, and imported GPX IDs/names after IMP_06. | Every source record includes source URL, source page/license note, destination filename, checksum, byte size, counts, imported ids, and imported names. | Source metadata is complete for staged GPX/FIT/synthetic files; imported IDs/names for the five required GPX files are recorded in DAT_03/IMP_06 mapping evidence. | PASS | [assets/DAT_03-source-manifest.txt](../assets/DAT_03-source-manifest.txt); [assets/DAT_03-imported-track-mapping.txt](../assets/DAT_03-imported-track-mapping.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DAT_03-source-manifest.txt](../assets/DAT_03-source-manifest.txt) | Source URLs, checksums, byte sizes, GPX counts, timestamp counts, and source track names. |
| [assets/DAT_03-imported-track-mapping.txt](../assets/DAT_03-imported-track-mapping.txt) | Imported GPX IDs and imported track names from IMP_06. |

## Screenshot Evidence

No screenshot required for this data-staging packet.

## Timings

| Step | Timing |
|---|---:|
| Data staging/metadata check | <1 min |

## Handoff Notes

- Completed: DAT_03.
- Remaining unfinished coverage: none for DAT_03.
- Blocked or not applicable: none.
- State left for the next packet: imported GPX ID/name mapping is available for later checks.
