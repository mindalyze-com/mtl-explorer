# Packet: FMT_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: FMT_01
- In scope: Verify server accepts .gpx, .fit, .tcx, .kml, .kmz, .igc, .nmea, .geojson, and .gdb formats; for this full regression, exercise one GPS-bearing sample per non-GPX/FIT format in addition to prior GPX/FIT coverage.
- Out of scope: Other coverage IDs except where noted as shared state/evidence.

## Prerequisites

- Required previous coverage IDs or run packets: GPX import packets and FIT packets terminal; local GPSBabel available for fully synthetic format sample generation.
- Required app/data state: Current run-state and packet set for this run.
- Required browser context: As specified by the coverage action.

## Allowed Mutations

- Allowed: Generate fully synthetic GPS-bearing samples, copy them to the watched folder, poll indexer/API, and update packet/run-state.
- Not allowed: Collapse this ID into a parent row or skip required direct evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FMT_01 | Generated distinct synthetic GPS tracks for TCX, KML, KMZ, IGC, NMEA, GeoJSON, and GDB; validated local sample geometry; copied all seven files to the watched folder; polled the app/indexer. | The server accepts the documented formats and each tested GPS-bearing sample indexes successfully, or any unsupported format is recorded with a precise reason. | GPX and FIT were already covered. All seven additional samples were accepted and indexed with loadStatus=SUCCESS and indexerStatus=COMPLETED_WITH_SUCCESS: format-sample.tcx, .kml, .kmz, .igc, .nmea, .geojson, and .gdb. Each accepted format produced a 24-point track; indexer reported completed=11, failed=0, removed=2 after the additions. | PASS | [assets/FMT_01-sample-generation.txt](../assets/FMT_01-sample-generation.txt); [assets/FMT_01-nmea-regeneration.txt](../assets/FMT_01-nmea-regeneration.txt); [assets/FMT_01-import-copy.txt](../assets/FMT_01-import-copy.txt); [assets/FMT_01-index-wait.txt](../assets/FMT_01-index-wait.txt); [assets/FIT_02-index-wait.txt](../assets/FIT_02-index-wait.txt); [assets/IMP_03-track-summary.txt](../assets/IMP_03-track-summary.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [assets/FMT_01-sample-generation.txt](../assets/FMT_01-sample-generation.txt) | Text/log evidence |
| [assets/FMT_01-nmea-regeneration.txt](../assets/FMT_01-nmea-regeneration.txt) | Text/log evidence |
| [assets/FMT_01-import-copy.txt](../assets/FMT_01-import-copy.txt) | Text/log evidence |
| [assets/FMT_01-index-wait.txt](../assets/FMT_01-index-wait.txt) | Text/log evidence |
| [assets/FIT_02-index-wait.txt](../assets/FIT_02-index-wait.txt) | Text/log evidence |
| [assets/IMP_03-track-summary.txt](../assets/IMP_03-track-summary.txt) | Text/log evidence |

## Screenshot Evidence

No screenshot evidence for this packet.

## Timings

| Step | Timing |
|---|---:|
| Synthetic format generation and validation | <1 second |
| Copy and indexing poll | 2 seconds |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
