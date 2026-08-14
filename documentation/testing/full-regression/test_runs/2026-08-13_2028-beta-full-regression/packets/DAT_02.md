# Packet: DAT_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: DAT_02.
- In scope: verify positive GPX fixtures have timestamped trackpoints.
- Out of scope: import and derived-stat correctness.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_01.
- Required app/data state: five staged public GPX files.
- Required browser context: none.

## Allowed Mutations

- Allowed: read staged GPX files and save validation evidence.
- Not allowed: modify or import the staged files.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DAT_02 | Parsed each complete `trkpt` element and counted elements that contain a `time` child. | Prefer timestamped trackpoints so duration, speed, moving time, and period statistics can be exercised. | All five files are fully timestamped: 7,735 of 7,735 trackpoints contain time values. | PASS | [assets/DAT_02-timestamps.txt](../assets/DAT_02-timestamps.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DAT_02-timestamps.txt](../assets/DAT_02-timestamps.txt) | Per-file trackpoint and timestamped-trackpoint counts. |

## Screenshot Evidence

Not applicable; this packet validates staged XML content.

## Timings

| Step | Timing |
|---|---:|
| Timestamp parsing | < 1 s |

## Handoff Notes

- Completed: all five positive GPX files are fully timestamped.
- Remaining unfinished coverage: DAT_03 onward.
- Blocked or not applicable: none.
- State left for the next packet: staged source metadata is ready; import IDs/names will be appended after indexing.
