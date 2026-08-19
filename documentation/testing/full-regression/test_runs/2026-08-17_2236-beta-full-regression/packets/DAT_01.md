# Packet: DAT_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: DAT_01
- In scope: At least five public Internet GPX files with real track sequences.
- Out of scope: Importing into the watched folder (IMP_02).

## Prerequisites

- Required previous coverage IDs or run packets: ACC_05.
- Required app/data state: Fresh empty database; fixture staging outside `data/gpx/`.
- Required browser context: None.

## Allowed Mutations

- Allowed: Download and stage public fixtures outside watched import folders.
- Not allowed: Use private GPX data or trigger indexing before IMP_01 baseline.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DAT_01 | Downloaded the five frozen-plan suggested GPX files, parsed their XML, and staged them under `data/logs/2026-08-17_2236-beta-full-regression-public-fixtures/` while confirming `data/gpx/` remained empty. | At least five public files contain `trk`/`trkseg`/`trkpt` sequences; waypoint-only files are excluded. | All five files contain real trackpoint sequences, with 381 to 2,954 `trkpt` elements each. None was placed in the watched import folder yet. | PASS | [assets/DAT_01-public-source-preflight.txt](../assets/DAT_01-public-source-preflight.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DAT_01-public-source-preflight.txt](../assets/DAT_01-public-source-preflight.txt) | URLs, filenames, sizes, checksums, trackpoint counts, and timestamps. |

## Screenshot Evidence

Not applicable; fixture structure was verified from the public files before import.

## Timings

| Step | Timing |
|---|---:|
| Local download and structural preflight | 4.4 s |
| Server staging outside watched folder | 5.0 s |

## Handoff Notes

- Completed: Five public, GPS-bearing, track-sequence GPX fixtures are staged.
- Remaining unfinished coverage: None for DAT_01.
- Blocked or not applicable: None.
- State left for the next packet: `data/gpx/` remains empty; staged fixtures are ready for IMP_02.
