# Packet: DAT_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: DAT_01
- In scope: Select at least five public internet GPX files with real track sequences.
- Out of scope: Importing the files into MTL Explorer; covered by IMP packets.

## Prerequisites

- Required previous coverage IDs or run packets: RUN_SETUP, ACC_01 through ACC_05.
- Required app/data state: source data staged outside the watched import folder.
- Required browser context: none.

## Allowed Mutations

- Allowed: download and stage public GPX files outside the watched import folder.
- Not allowed: use private/local GPX tracks or waypoint-only files as positive evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DAT_01 | Downloaded five public GPX files from `gps-touring/sample-gpx`, parsed them, and copied them to the target source-data directory outside `data/gpx`. | At least five public internet GPX files have real `trk` / `trkseg` / `trkpt` sequences. | PASS: all five staged GPX files contain real trackpoint sequences: 1,414; 2,954; 1,688; 1,298; and 381 `trkpt` elements respectively. | PASS | [assets/DAT_public-data-metadata.txt](../assets/DAT_public-data-metadata.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DAT_public-data-metadata.txt](../assets/DAT_public-data-metadata.txt) | Public GPX source URLs, source notes, checksums, byte sizes, track counts, trackpoint counts, timestamp counts, and track names. |

## Screenshot Evidence

Not applicable; this is a data-source validation check.

## Timings

| Step | Timing |
|---|---:|
| GPX download, parse, and source-data staging | <1 minute |

## Handoff Notes

- Completed: DAT_01 is terminal.
- Remaining unfinished coverage: DAT_02 onward.
- Blocked or not applicable: none.
- State left for the next packet: five public GPX files remain staged in `/root/mtl-full-regression-2026-06-20_2114-beta-178-full-regression/source-data`, outside the watched import folder.
