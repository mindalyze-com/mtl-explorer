# Packet: DAT_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: DAT_01
- In scope: At least five public internet GPX files with real track sequences.
- Out of scope: Importing them into the watched folder.

## Prerequisites

- Required previous coverage IDs or run packets: ACC_05.
- Required app/data state: Empty app dataset; unwatched staging folder available.
- Required browser context: None.

## Allowed Mutations

- Allowed: Download public GPX files to disposable unwatched staging.
- Not allowed: Use private local GPX data or copy fixtures into repository artifacts.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DAT_01 | Download five suggested public GPX files into unwatched staging and parse each XML document for `trkpt`. | At least five public files contain real `trk/trkseg/trkpt` sequences; waypoint-only files do not count. | Five public files parsed successfully with 381-2,954 trackpoints each and 7,735 total trackpoints. | PASS | [assets/DAT_01-public-gpx.txt](../assets/DAT_01-public-gpx.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DAT_01-public-gpx.txt](../assets/DAT_01-public-gpx.txt) | Source URLs, source/license note, checksums, sizes, and trackpoint counts. |

## Screenshot Evidence

Not useful for disposable file validation.

## Timings

| Step | Timing |
|---|---:|
| Download and XML validation | <10 s |

## Handoff Notes

- Completed: Five public positive GPX fixtures staged and validated.
- Remaining unfinished coverage: None for DAT_01.
- Blocked or not applicable: Repository source has no visible root license file; recorded as a documentation gap, not an import blocker.
- State left for the next packet: Five GPX fixtures remain outside `data/gpx/` until IMP_02.
