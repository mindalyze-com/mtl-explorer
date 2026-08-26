# Packet: FMT_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FMT_01
- In scope: Test one GPS-bearing sample for every supported extension: GPX, FIT, TCX, KML, KMZ, IGC, NMEA, GeoJSON, and GDB.
- Out of scope: Per-format UI/details/download parity, covered by FMT_02.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_07, IMP_02-IMP_09, and FIT_01-FIT_05.
- Required app/data state: Working watched GPS folder and packaged GPSBabel.
- Required browser context: None for acceptance/index evidence.

## Allowed Mutations

- Allowed: Generate non-private format derivatives from the synthetic six-point track and copy them into the watched run subfolder.
- Not allowed: Use local/private tracks or handwave untested extensions.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FMT_01 | Validate/import one GPS-bearing file for all nine supported extensions and record every product conversion/index result. | Every available format is directly tested or terminal with a reason. | GPX and FIT passed earlier flows. TCX, KML, KMZ, IGC, NMEA, GeoJSON, and GDB all converted and indexed SUCCESS as tracks 100006-100012. | PASS | [assets/FMT_01-formats.txt](../assets/FMT_01-formats.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FMT_01-formats.txt](../assets/FMT_01-formats.txt) | Fixture provenance, validation, checksums, track IDs, and ingest statuses for all formats. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; exact converter/index evidence is recorded in the linked asset.

## Timings

| Step | Timing |
|---|---:|
| Fixture generation/validation | 8 min |
| Seven-format live import/index | <1 min |

## Handoff Notes

- Completed: Direct acceptance/index coverage for every supported extension.
- Remaining unfinished coverage: None for FMT_01.
- Blocked or not applicable: None.
- State left for the next packet: Seven new format tracks await browser freshness reload; 13 total tracks expected.
