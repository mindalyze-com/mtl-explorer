# Packet: DAT_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: DAT_01.
- In scope: obtain at least five public internet GPX files and prove each contains real track sequences.
- Out of scope: import/index behavior and timestamp completeness.

## Prerequisites

- Required previous coverage IDs or run packets: RUN_SETUP, ACC_01-ACC_05.
- Required app/data state: empty watched import folder.
- Required browser context: signed-in desktop map remains unchanged.

## Allowed Mutations

- Allowed: download public GPX files into a staging folder outside `data/gpx`.
- Not allowed: place files in the watched folder or trigger indexing.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DAT_01 | Downloaded the five frozen-plan GPX examples from the public `gps-touring/sample-gpx` repository into the disposable staging folder; counted `trk`, `trkseg`, and `trkpt` elements; confirmed the watched folder stayed empty. | At least five public GPX files contain real `trk`/`trkseg`/`trkpt` sequences and are not waypoint-only. | Five files were staged. Each has at least one track/segment and between 381 and 2,954 trackpoints. The watched import folder still contained zero files. | PASS | [assets/DAT_01-public-gpx.txt](../assets/DAT_01-public-gpx.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DAT_01-public-gpx.txt](../assets/DAT_01-public-gpx.txt) | Public source URLs, source/license note, filenames, checksums, sizes, element counts, and staging-state evidence. |

## Screenshot Evidence

Not applicable; this packet validates staged public data before UI import.

## Timings

| Step | Timing |
|---|---:|
| Download and validation | 5 s |

## Handoff Notes

- Completed: five positive public GPX fixtures staged and validated.
- Remaining unfinished coverage: DAT_02 onward.
- Blocked or not applicable: none.
- State left for the next packet: five files in `test-data/public`; watched folder remains empty.
