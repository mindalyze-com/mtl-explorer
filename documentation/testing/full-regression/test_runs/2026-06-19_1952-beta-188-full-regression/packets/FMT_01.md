# Packet: FMT_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: FMT_01
- In scope: Verify server acceptance for `.gpx`, `.fit`, `.tcx`, `.kml`, `.kmz`, `.igc`, `.nmea`, `.geojson`, and `.gdb` with GPS-bearing samples.
- Out of scope: Full per-format display/details/download verification; covered by FMT_02.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_06 and FIT_02.
- Required app/data state: GPX and FIT imports already successful; format samples may be added to the watched import folder.
- Required browser context: none.

## Allowed Mutations

- Allowed: Generate synthetic GPS-bearing format samples, delete/retry FMT-only test files, and import final `fmt_*_unique` files.
- Not allowed: Change GPX/FIT baseline files unrelated to FMT coverage.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FMT_01 | Used prior GPX/FIT import evidence, then generated and imported unique synthetic `.tcx`, `.kml`, `.kmz`, `.igc`, `.nmea`, `.geojson`, and `.gdb` files through the watched folder. | Server accepts each documented supported track format with GPS-bearing data, or records untested formats with reason. | `.gpx` and `.fit` were accepted earlier. Final non-GPX batch imported all seven remaining formats as unique tracks with `COMPLETED_WITH_SUCCESS`, `SUCCESS`, and 10 points each: ids 100014-100020. | PASS | [assets/FMT_01-format-import.txt](../assets/FMT_01-format-import.txt); [assets/DAT_03-imported-track-mapping.txt](../assets/DAT_03-imported-track-mapping.txt); [assets/FIT_02-import-monitor.txt](../assets/FIT_02-import-monitor.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FMT_01-format-import.txt](../assets/FMT_01-format-import.txt) | Synthetic sample manifest and final import mapping for non-GPX formats. |
| [assets/DAT_03-imported-track-mapping.txt](../assets/DAT_03-imported-track-mapping.txt) | Earlier GPX import mapping. |
| [assets/FIT_02-import-monitor.txt](../assets/FIT_02-import-monitor.txt) | Earlier FIT import/index evidence. |

## Screenshot Evidence

No screenshot required for server acceptance; UI verification continues in FMT_02.

## Timings

| Step | Timing |
|---|---:|
| Generate unique format samples | <1 min |
| Import and settle final FMT batch | 55.5 s |

## Handoff Notes

- Completed: FMT_01.
- Remaining unfinished coverage: FMT_02 onward.
- Blocked or not applicable: none.
- State left for the next packet: Seven unique non-GPX tracks remain imported as ids 100014-100020; total visible tracks is 11.
