# Packet: DAT_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: DAT_03.
- In scope: durable source URL/page/license note, destination filename, checksum, size, trackpoint/timestamp counts, and eventual imported ID/name mapping for every positive source file.
- Out of scope: none.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_01, DAT_02; later IMP_06 and FIT_02 for completion.
- Required app/data state: staged public files now; indexed imports later.
- Required browser context: signed-in desktop context for later ID/name verification.

## Allowed Mutations

- Allowed: save source metadata now and update this same packet after imports.
- Not allowed: invent IDs/names or mark terminal before the complete mapping exists.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DAT_03 | Recorded source page/license note, raw URLs, destination filenames, SHA-256, byte size, trackpoint/GPS-record count, timestamp count, and imported ID/name mappings as app state became available. | Every source file has complete source/file/content metadata plus imported track ID(s) and name(s). | Complete for all six sources: GPX IDs 100000-100004 and FIT ID 100005 with Track Details name Activity.fit (Track Browser label Track 100005). | PASS | [assets/DAT_03-source-mapping.txt](../assets/DAT_03-source-mapping.txt); [assets/FIT_02-search.webp](../assets/FIT_02-search.webp); [assets/FIT_02-detail.webp](../assets/FIT_02-detail.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DAT_03-source-mapping.txt](../assets/DAT_03-source-mapping.txt) | Resumable source/import mapping with explicit pending fields. |

## Screenshot Evidence

FIT and GPX imported-name/ID evidence is linked from FIT_02 and IMP_06.

## Timings

| Step | Timing |
|---|---:|
| Source metadata assembly | < 1 min |
| Imported mapping verification | 6 min across IMP_06 and FIT_02 |

## Handoff Notes

- Completed: full source/file/content metadata and imported ID/name mappings for all five GPX files and the FIT source.
- Remaining unfinished coverage: none for DAT_03.
- Blocked or not applicable: none.
- State left for the next packet: durable six-source mapping is complete.
