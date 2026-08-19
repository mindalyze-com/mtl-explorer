# Packet: FMT_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FMT_01
- In scope: Positive GPS-bearing acceptance for every listed track-file format.
- Out of scope: Full per-format details, chart, statistics, and download-content checks in FMT_02.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_01-DAT_07, IMP_01-IMP_09, FIT_01-FIT_06.
- Required app/data state: Six existing GPX/FIT-backed tracks; GPSBabel 1.10.0 available.
- Required browser context: Signed-in Admin Processing, map, and Track Details.

## Allowed Mutations

- Allowed: Stage synthetic positive non-GPX samples, copy them into a run-specific watched folder, wait for processing, and accept the freshness reload.
- Not allowed: Count a renamed or GPS-empty file as positive evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FMT_01 | Preflighted GPS-bearing TCX, KML, real ZIP-based KMZ, IGC, NMEA, GeoJSON, and GDB samples; copied all seven into the watched folder; monitored processing to completion; reloaded the UI; opened all seven resulting detail routes. Existing positive GPX and FIT imports complete the list. | The server accepts one positive sample for each of `.gpx`, `.fit`, `.tcx`, `.kml`, `.kmz`, `.igc`, `.nmea`, `.geojson`, and `.gdb`, or records a format gap. | Every named format was accepted. Seven new files became tracks 100006-100012 with successful conversion/indexing. Three geometry-identical tracks were automatically duplicate-excluded from the default map, but their signed-in detail routes remain valid. | PASS | [assets/FMT_01-acceptance.txt](../assets/FMT_01-acceptance.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FMT_01-acceptance.txt](../assets/FMT_01-acceptance.txt) | Per-format hashes, track IDs, structural preflight, live conversion, settled processing, and UI route evidence. |

## Screenshot Evidence

Blocked by ACC_04; DOM, route, Admin Processing, checksum, and server conversion evidence is recorded.

## Timings

| Step | Timing |
|---|---:|
| Generate/correct/preflight seven samples | About 4 min |
| Copy, live conversion, and GPS indexing | About 13 s |
| Follow-on processing settled | About 1m 45s |
| Reload and UI route validation | About 3 min |

## Handoff Notes

- Completed: All nine listed formats have direct positive acceptance evidence.
- Remaining unfinished coverage: FMT_02 full child checks for each non-GPX track.
- Blocked or not applicable: Screenshot capture remains blocked under ACC_04.
- State left for the next packet: Thirteen total indexed tracks; ten visible by default because three new format tracks are duplicate-marked.
