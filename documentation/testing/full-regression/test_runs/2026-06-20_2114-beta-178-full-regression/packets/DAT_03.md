# Packet: DAT_03

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: DAT_03
- In scope: Record public source URL, source note, destination filename, SHA-256, byte size, `trkpt` count, timestamp count, imported IDs, and imported names.
- Out of scope: Performing imports before the IMP/FIT packets.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_01, DAT_02.
- Required app/data state: public GPX/FIT files downloaded, parsed, imported, and mapped.
- Required browser context: none.

## Allowed Mutations

- Allowed: record source metadata and imported ID/name mappings from completed import evidence.
- Not allowed: invent imported IDs.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DAT_03 | Recorded source URL, source page/license note, destination filename, SHA-256, byte size, `trkpt` count, timestamp count, source track names, and imported ID/name mappings for the staged public GPX/FIT files. | Every source file has full source metadata, and imported ID/name mappings are recorded after import. | PASS: source metadata is complete for five GPX files and one FIT file; the five GPX source files map to track IDs 100000-100004 and `Activity.fit` maps to visible UI track `Track 100005` at ID 100005. | PASS | [assets/DAT_public-data-metadata.txt](../assets/DAT_public-data-metadata.txt); [assets/DAT_03-imported-mapping.txt](../assets/DAT_03-imported-mapping.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DAT_public-data-metadata.txt](../assets/DAT_public-data-metadata.txt) | Public source metadata, checksums, sizes, parsed counts, and source notes. |
| [assets/DAT_03-imported-mapping.txt](../assets/DAT_03-imported-mapping.txt) | Imported GPX and FIT track IDs/names after IMP_06 and FIT_02. |

## Screenshot Evidence

Not applicable; this is a data-source validation check.

## Timings

| Step | Timing |
|---|---:|
| Source metadata generation | <1 minute |

## Handoff Notes

- Completed: DAT_03 is terminal with source metadata and imported mappings.
- Remaining unfinished coverage: none for DAT_03.
- Blocked or not applicable: none.
- State left for the next packet: GPX and FIT mappings are available to later details/download checks.
