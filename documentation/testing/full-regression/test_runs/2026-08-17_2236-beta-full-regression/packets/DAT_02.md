# Packet: DAT_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: DAT_02
- In scope: Prefer GPX files with timestamped trackpoints.
- Out of scope: Derived duration and speed checks after import.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_01.
- Required app/data state: Five staged public GPX files.
- Required browser context: None.

## Allowed Mutations

- Allowed: Read-only XML timestamp count.
- Not allowed: Edit public fixtures.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DAT_02 | Counted `trkpt` elements and their direct `time` children in all five staged GPX files. | Prefer timestamped trackpoints so duration, speed, moving time, and period statistics can be tested. | Every trackpoint in every file has a timestamp: 1,414/1,414; 2,954/2,954; 1,688/1,688; 1,298/1,298; and 381/381. | PASS | [assets/DAT_01-public-source-preflight.txt](../assets/DAT_01-public-source-preflight.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DAT_01-public-source-preflight.txt](../assets/DAT_01-public-source-preflight.txt) | Per-file trackpoint and trackpoint-timestamp counts. |

## Screenshot Evidence

Not applicable; this is fixture XML evidence.

## Timings

| Step | Timing |
|---|---:|
| XML count | <1 s |

## Handoff Notes

- Completed: All five public GPX fixtures are fully timestamped at trackpoint level.
- Remaining unfinished coverage: None for DAT_02.
- Blocked or not applicable: None.
- State left for the next packet: Timestamped fixtures remain staged outside the watcher.
