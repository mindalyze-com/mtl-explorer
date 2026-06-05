# Packet: FMT_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: FMT_01
- In scope: Verify the server accepts `.gpx`, `.fit`, `.tcx`, `.kml`, `.kmz`, `.igc`, `.nmea`, `.geojson`, and `.gdb` with GPS-bearing samples.
- Out of scope: Per-format UI details, downloads, charts, and statistics; covered by FMT_02.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_01-IMP_09, FIT_01-FIT_06.
- Required app/data state: Three remaining GPX tracks and one FIT-backed track were already imported successfully.
- Required browser context: None.

## Allowed Mutations

- Allowed: Generate fully synthetic GPS-bearing samples for remaining formats and place them into the watched import folder.
- Not allowed: Use private GPX data or change product source/configuration.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FMT_01 | Reused previous `.gpx` and `.fit` successful imports, generated synthetic GPS-bearing `.tcx`, `.kml`, `.kmz`, `.igc`, `.nmea`, `.geojson`, and `.gdb` samples, placed them in `/app/gpx`, and waited for indexing/jobs to settle. | Server accepts each listed format, or unsupported/uncovered formats are recorded. | All listed formats were accepted in this run. Existing GPX and FIT imports passed earlier. Synthetic TCX, KML, IGC, GeoJSON, KMZ, GDB, and corrected valid-fix NMEA samples indexed successfully as tracks `100006`, `100007`, `100008`, `100010`, `100011`, `100012`, and `100013`. Indexer status after import: `total=13 completed=11 removed=2 failed=0 pending=0`; visible track count `11`; duplicate/activity/exploration jobs all `11/11`. | PASS | [assets/FMT_01-synthetic-generation.txt](../assets/FMT_01-synthetic-generation.txt), [assets/FMT_01-index-logs.txt](../assets/FMT_01-index-logs.txt), [assets/FMT_01-nmea-regeneration.txt](../assets/FMT_01-nmea-regeneration.txt), [assets/FMT_01-nmea-reindex-logs.txt](../assets/FMT_01-nmea-reindex-logs.txt), [assets/FMT_01-post-format-status-api.txt](../assets/FMT_01-post-format-status-api.txt), previous GPX/FIT packets |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FMT_01-synthetic-generation.txt](../assets/FMT_01-synthetic-generation.txt) | Generated sample file paths, formats, byte counts, and checksums for TCX/KML/IGC/NMEA/GeoJSON/GDB/KMZ. |
| [assets/FMT_01-index-logs.txt](../assets/FMT_01-index-logs.txt) | Compact watcher/index summary for all generated non-GPX samples. |
| [assets/FMT_01-nmea-regeneration.txt](../assets/FMT_01-nmea-regeneration.txt) | Correction of the synthetic NMEA file to valid-fix RMC/GGA sentences. |
| [assets/FMT_01-nmea-reindex-logs.txt](../assets/FMT_01-nmea-reindex-logs.txt) | NMEA reindex summary showing final `SUCCESS`. |
| [assets/FMT_01-post-format-status-api.txt](../assets/FMT_01-post-format-status-api.txt) | Authenticated indexer/job/track API summary showing all accepted format tracks. |

## Timings

| Step | Timing |
|---|---:|
| Synthetic format generation | <1 second |
| Initial format watcher/import pass | ~13 seconds |
| NMEA correction and reindex | ~16 seconds |

## Handoff Notes

- Completed: FMT_01 terminal as `PASS`.
- Remaining unfinished coverage: Continue with `FMT_02` for display, details/charts, statistics inclusion, and downloads for each non-GPX format tested.
- Blocked or not applicable: None.
- State left for the next packet: Eleven visible tracks remain: three GPX-backed, one FIT-backed, and seven synthetic non-GPX format tracks.
