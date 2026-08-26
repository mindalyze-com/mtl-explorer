# Packet: DAT_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: DAT_02
- In scope: Timestamp coverage in the five positive GPX fixtures.
- Out of scope: Derived statistics before import.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_01.
- Required app/data state: Five staged GPX files.
- Required browser context: None.

## Allowed Mutations

- Allowed: Read-only XML inspection.
- Not allowed: Modify fixture content.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DAT_02 | Count `trkpt` elements and `time` children in each staged file. | Prefer timestamped trackpoints so duration, speed, moving time, and period stats can be tested. | Every one of 7,735 trackpoints across all five files has a timestamp. | PASS | [assets/DAT_02-timestamps.txt](../assets/DAT_02-timestamps.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DAT_02-timestamps.txt](../assets/DAT_02-timestamps.txt) | Per-file trackpoint and timestamped-trackpoint counts. |

## Screenshot Evidence

Not useful for XML timestamp validation.

## Timings

| Step | Timing |
|---|---:|
| Timestamp validation | <1 s |

## Handoff Notes

- Completed: All positive GPX fixtures are fully timestamped.
- Remaining unfinished coverage: None for DAT_02.
- Blocked or not applicable: None.
- State left for the next packet: Files remain staged and unmodified.
