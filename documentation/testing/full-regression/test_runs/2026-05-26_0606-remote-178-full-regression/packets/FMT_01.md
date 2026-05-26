# Packet: FMT_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: FMT_01
- In scope: acceptance/conversion of supported track formats: `.gpx`, `.fit`, `.tcx`, `.kml`, `.kmz`, `.igc`, `.sbp`, `.nmea`, `.geojson`, `.gdb`.
- Out of scope: full per-format details/download workflow, covered by FMT_02.

## Prerequisites

- Required previous coverage IDs or run packets: DAT, IMP, DEL, FIT packets complete.
- Required app/data state: installed app running with watched import folder available.
- Required browser context: desktop signed-in browser plus authenticated installed-app API checks.

## Allowed Mutations

- Allowed: generate GPS-bearing non-GPX samples from public GPX source data, copy them into `./data/gpx`, wait for indexer, refresh UI.
- Not allowed: product source edits, server workarounds, or manual database changes.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FMT_01 | Tested supported-format import using `.gpx`, `.fit`, `.tcx`, `.kml`, `.kmz`, `.igc`, `.nmea`, `.geojson`, and `.gdb`; searched for an SBP sample/generator. | Server accepts all listed formats with GPS-bearing samples, or a format is explicitly terminal with a concrete constraint. | GPX and FIT already passed earlier. Distinct TCX, KML, IGC, GDB, and NMEA samples imported successfully. KMZ failed conversion with `Input type 'kmz' not recognized`. GeoJSON converted but produced `EMPTY_FILE` / 0 trackpoints. SBP remained blocked because no sample was found and installed GPSBabel lists SBP as read-only. | FAIL | `assets/FMT_01-unique-format-status.txt`, `assets/FMT_01-unique-track-api-summary.txt`, `assets/FMT_01-sbp-source-search.txt`, `assets/FMT_01-track-browser-unique.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| FMT-01 | P2 | KMZ is listed as supported but GPSBabel conversion fails. | Place `FMT_KMZ_unique.kmz` in `./data/gpx` and wait for the watcher. | KMZ imports as a usable GPS track. | Conversion fails: `Input type 'kmz' not recognized`; no user-visible track is created. | `assets/FMT_01-unique-format-status.txt` | Users cannot import KMZ tracks despite the supported-format expectation. |
| FMT-02 | P2 | GeoJSON import produces an empty track. | Place `FMT_GEOJSON_unique.geojson` in `./data/gpx` and wait for the watcher. | GeoJSON imports as a GPS-bearing track with map/list/stat surfaces. | Conversion completes but ingest reports `EMPTY_FILE`; API summary shows 0 points and null distance. | `assets/FMT_01-unique-format-status.txt`, `assets/FMT_01-unique-track-api-summary.txt` | GeoJSON support is not usable for this GPS-bearing sample. |
| FMT-03 | P3 | SBP positive coverage could not be completed in this run. | Search local/repo/target files and installed GPSBabel capabilities for `.sbp` sample generation. | A GPS-bearing SBP sample is available or generated for import testing. | No sample found; installed GPSBabel lists SBP as read-only, so the run could not generate one. | `assets/FMT_01-sbp-source-search.txt`, `assets/FMT_01-gpsbabel-formats-head.txt` | SBP remains unverified; this is a coverage constraint rather than a confirmed product defect. |

## Evidence Files

| File | Purpose |
|---|---|
| assets/FMT_01-gpsbabel-formats-head.txt | Cropped GPSBabel format capability list. |
| assets/FMT_01-unique-format-sources.txt | Distinct per-format sample generation checksums. |
| assets/FMT_01-unique-format-import.txt | Watched-folder copy of unique format samples. |
| assets/FMT_01-unique-format-status.txt | Watcher/import status for unique format samples. |
| assets/FMT_01-unique-track-api-summary.txt | Track IDs, source filenames, point counts, and distances for FMT imports. |
| assets/FMT_01-track-browser-unique.webp | Track browser evidence for visible format imports. |
| assets/FMT_01-all-track-browser-after-unique.webp | Track browser after unique format imports. |
| assets/FMT_01-sbp-source-search.txt | SBP sample/capability constraint summary. |

## Timings

| Step | Timing |
|---|---:|
| Unique format generation/import | <5m |
| Browser/API verification | <5m |

## Handoff Notes

- Completed: FMT_01 terminal FAIL with direct evidence for successful and failed formats.
- Remaining unfinished coverage: none for FMT_01.
- Blocked or not applicable: SBP positive sample blocked by unavailable sample/generator.
- State left for the next packet: dataset now includes prior GPX/FIT tracks plus visible TCX/IGC/GDB/NMEA format tracks; KMZ failed and GeoJSON is empty.
