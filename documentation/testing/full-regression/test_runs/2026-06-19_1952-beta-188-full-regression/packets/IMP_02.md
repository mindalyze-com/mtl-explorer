# Packet: IMP_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: IMP_02
- In scope: Import the five public GPX files through the documented watched import folder.
- Out of scope: Waiting for indexing completion or UI verification after import.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_01.
- Required app/data state: Empty baseline captured; five public GPX files staged outside watched folder.
- Required browser context: none.

## Allowed Mutations

- Allowed: Copy the five public GPX files into the README-documented watched import folder.
- Not allowed: Copy FIT, synthetic, or non-GPX format files for this packet.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| IMP_02 | Copied the five staged public GPX files into `/root/mtl-full-regression-2026-06-19_1952-beta-188-full-regression/data/gpx`. | The five GPX files are imported through the documented watched folder, ready for automatic indexing. | Watched folder contains exactly the five public GPX files for this import step, with sizes matching the source manifest. | PASS | [assets/IMP_02-copy-gpx.txt](../assets/IMP_02-copy-gpx.txt); [assets/DAT_03-source-manifest.txt](../assets/DAT_03-source-manifest.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/IMP_02-copy-gpx.txt](../assets/IMP_02-copy-gpx.txt) | Destination watched-folder listing after copying the five GPX files. |
| [assets/DAT_03-source-manifest.txt](../assets/DAT_03-source-manifest.txt) | Source checksums and trackpoint counts for copied files. |

## Screenshot Evidence

No screenshot required for watched-folder copy.

## Timings

| Step | Timing |
|---|---:|
| Copy five GPX files into watched folder | <1 min |

## Handoff Notes

- Completed: IMP_02.
- Remaining unfinished coverage: IMP_03 onward; DAT_03 imported ID/name mapping still pending.
- Blocked or not applicable: none.
- State left for the next packet: five public GPX files are present in the watched import folder and should be indexed automatically.
