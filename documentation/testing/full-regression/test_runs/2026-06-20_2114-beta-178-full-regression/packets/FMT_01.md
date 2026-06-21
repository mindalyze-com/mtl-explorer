# Packet: FMT_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: FMT_01
- In scope: Verify accepted GPS source extensions: `.gpx`, `.fit`, `.tcx`, `.kml`, `.kmz`, `.igc`, `.nmea`, `.geojson`, and `.gdb`.
- Out of scope: Full per-format detail/download behavior; covered by FMT_02 and prior FIT packets.

## Prerequisites

- Required previous coverage IDs or run packets: FIT_02.
- Required app/data state: Authenticated run with upload directory available.
- Required browser context: authenticated desktop browser session for upload API calls.

## Allowed Mutations

- Allowed: upload synthetic GPS-bearing samples for supported formats.
- Not allowed: use private local GPX tracks or alter server configuration.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FMT_01 | Generated synthetic GPS-bearing sample files and uploaded `.gpx`, `.tcx`, `.kml`, `.kmz`, `.igc`, `.nmea`, `.geojson`, and `.gdb`; used existing `Activity.fit` coverage for `.fit`. | The server accepts `.gpx`, `.fit`, `.tcx`, `.kml`, `.kmz`, `.igc`, `.nmea`, `.geojson`, and `.gdb`. For a full release regression, each available format is tested or explicitly marked not covered with a reason. | PASS: all newly uploaded synthetic extensions returned HTTP 200, saved under their source filenames, and indexed successfully with 24 points each. `.fit` was accepted and indexed earlier as `Activity.fit` in FIT_01/FIT_02. | PASS | [assets/FMT_01-upload-indexing.txt](../assets/FMT_01-upload-indexing.txt); [assets/FMT_01-sample-generation.txt](../assets/FMT_01-sample-generation.txt); [assets/FMT_01-local-conversion-smoke.txt](../assets/FMT_01-local-conversion-smoke.txt); [packets/FIT_02.md](FIT_02.md) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FMT_01-upload-indexing.txt](../assets/FMT_01-upload-indexing.txt) | Upload response and indexed-track matrix for the supported format samples. |
| [assets/FMT_01-sample-generation.txt](../assets/FMT_01-sample-generation.txt) | Synthetic sample provenance, sizes, and checksums. |
| [assets/FMT_01-local-conversion-smoke.txt](../assets/FMT_01-local-conversion-smoke.txt) | Local GPSBabel smoke test matching server conversion formats before upload. |
| [packets/FIT_02.md](FIT_02.md) | Prior FIT acceptance and indexing evidence. |

## Screenshot Evidence

No screenshot required; this packet is a server acceptance/indexing matrix backed by upload and index APIs.

## Timings

| Step | Timing |
|---|---:|
| Synthetic sample generation | ~1 second |
| Upload and index polling | ~15 seconds |

## Handoff Notes

- Completed: FMT_01 is terminal.
- Remaining unfinished coverage: FMT_02 onward.
- Blocked or not applicable: none.
- State left for the next packet: synthetic format tracks are imported as IDs 100006 through 100013; FIT remains ID 100005.
