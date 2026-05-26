# Packet: FMT_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: FMT_02
- In scope: per-format upload acceptance, GPSBabel conversion, map/list/stat display, details/charts reachability, and original/GPX downloads for non-GPX formats.
- Out of scope: GPX/FIT baseline coverage already covered by IMP/FIT packets.

## Prerequisites

- Required previous coverage IDs or run packets: FMT_01 evidence collected.
- Required app/data state: unique non-GPX samples staged in `./data/gpx`.
- Required browser context: desktop signed-in browser; authenticated API used for download verification because in-app browser download events are unsupported.

## Allowed Mutations

- Allowed: open track browser/details, use visible download controls where supported, verify downloads through authenticated installed-app endpoints.
- Not allowed: product source edits, database edits, or conversion workarounds.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FMT_02 | Verified non-GPX imports across UI/API, attempted user-facing detail navigation, and downloaded original plus GPX exports for imported non-GPX tracks. | Each tested non-GPX format is accepted, converted, visible on map/list/stats/details/charts, and supports original plus GPX download. | TCX, IGC, GDB, and NMEA were visible in track browser/stat totals and download endpoints returned matching originals plus GPX exports with 180 `trkpt`. KML imported and download endpoints worked, but it was not visible/searchable in the track browser. GeoJSON exported GPX with 0 `trkpt`. KMZ had no imported track because conversion failed. Per-format details/charts could not be completed for every format. | FAIL | `assets/FMT_02-unique-download-verification.txt`, `assets/FMT_01-all-track-browser-after-unique.txt`, `assets/FMT_01-kml-search-probes.txt`, `assets/FMT_02-tcx-detail-overview.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| FMT-01 | P2 | KMZ has no per-format workflow because import fails. | Import `FMT_KMZ_unique.kmz`. | KMZ appears on map/list/details/stats and supports downloads. | No track is created due `Input type 'kmz' not recognized`. | `assets/FMT_01-unique-format-status.txt` | KMZ users cannot complete the import workflow. |
| FMT-02 | P2 | GeoJSON exports no trackpoints after import. | Import `FMT_GEOJSON_unique.geojson`, then download GPX for track #100016. | Exported GPX contains real trackpoints and the track appears in UI surfaces. | Download endpoint returns GPX with 0 `<trkpt>` and UI does not show a usable track. | `assets/FMT_02-unique-download-verification.txt`, `assets/FMT_01-unique-track-api-summary.txt` | GeoJSON users may see a completed source file without usable track data. |
| FMT-04 | P2 | KML import is not user-visible in the track browser. | Import `FMT_KML_unique.kml`, refresh, then search `KML`, `FMT_KML`, `Path`, or track id `100014` in Tracks. | KML track is searchable/listed like other imported tracks. | API reports track #100014 with 180 points, but UI searches return 0 results and the 10-row track browser list omits it. | `assets/FMT_01-kml-search-probes.txt`, `assets/FMT_01-all-track-browser-after-unique.txt`, `assets/FMT_01-unique-track-api-summary.txt` | A successful KML import can be effectively hidden from the user. |

## Evidence Files

| File | Purpose |
|---|---|
| assets/FMT_02-unique-download-verification.txt | Original/download-as-GPX HTTP, checksum, and trkpt checks for non-GPX imports. |
| assets/FMT_02-unique-*-original.* | Downloaded original source files for unique non-GPX imports. |
| assets/FMT_02-unique-*-export.gpx | Downloaded GPX exports for unique non-GPX imports. |
| assets/FMT_01-all-track-browser-after-unique.txt | Track browser row summary after unique imports. |
| assets/FMT_01-kml-search-probes.txt | KML search probes showing no visible track result. |
| assets/FMT_02-tcx-detail-overview.txt | Detail-navigation attempt note for format row. |
| assets/FMT_02-tcx-detail-overview.webp | Screenshot from detail-navigation attempt. |

## Timings

| Step | Timing |
|---|---:|
| Format UI/API verification | <10m |

## Handoff Notes

- Completed: FMT_02 terminal FAIL.
- Remaining unfinished coverage: none for FMT_02.
- Blocked or not applicable: browser download events unsupported; download files verified through authenticated installed-app endpoints.
- State left for the next packet: app remains running, signed-in desktop browser available, current dataset includes added format-test tracks.
