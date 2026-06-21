# Packet: DAT_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md` required data-change section.
- Coverage ID or run packet: DAT_01
- In scope: Five public GPX files with real track sequences.
- Out of scope: Importing staged files into the watched folder unless covered by IMP/FIT/FMT packets.

## Prerequisites

- Required previous coverage IDs or run packets: preceding DAT packets.
- Required app/data state: source files staged under `/root/mtl-full-regression-2026-06-19_1952-beta-188-full-regression/source-data`, outside the watched import folder.
- Required browser context: none.

## Allowed Mutations

- Allowed: stage public or synthetic test files outside watched import folder; update this packet and run-state.
- Not allowed: copy staged files into `data/gpx` for DAT-only packets.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DAT_01 | Downloaded and parsed the five regression-plan public GPX files from gps-touring/sample-gpx. | At least five public internet GPX files have real `trk`/`trkseg`/`trkpt` sequences and are not waypoint-only. | All five staged GPX files have real trackpoint sequences, with counts from 381 to 2,954 trackpoints. | PASS | [assets/DAT_01-data-files.txt](../assets/DAT_01-data-files.txt); [assets/DAT_03-source-manifest.txt](../assets/DAT_03-source-manifest.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DAT_01-data-files.txt](../assets/DAT_01-data-files.txt) | Evidence for DAT_01. |
| [assets/DAT_03-source-manifest.txt](../assets/DAT_03-source-manifest.txt) | Evidence for DAT_01. |

## Screenshot Evidence

No screenshot required for this data-staging packet.

## Timings

| Step | Timing |
|---|---:|
| Data staging/metadata check | <1 min |

## Handoff Notes

- Completed: DAT_01.
- Remaining unfinished coverage: DAT_02 onward.
- Blocked or not applicable: none.
- State left for the next packet: staged source data remains outside watched import folder. 
