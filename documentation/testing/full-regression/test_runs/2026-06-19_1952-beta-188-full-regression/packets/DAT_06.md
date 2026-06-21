# Packet: DAT_06

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md` required data-change section.
- Coverage ID or run packet: DAT_06
- In scope: Do not count invalid data as positive evidence.
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
| DAT_06 | Reviewed staged positive data and separated negative-file behavior from positive import evidence. | Non-GPS FIT files and waypoint-only GPX files are not counted as positive evidence. | Only five trackpoint GPX files and the public Activity.fit sample are counted as positive evidence; negative samples are not used as successful import proof. | PASS | [assets/DAT_06-positive-data-scope.txt](../assets/DAT_06-positive-data-scope.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DAT_06-positive-data-scope.txt](../assets/DAT_06-positive-data-scope.txt) | Evidence for DAT_06. |

## Screenshot Evidence

No screenshot required for this data-staging packet.

## Timings

| Step | Timing |
|---|---:|
| Data staging/metadata check | <1 min |

## Handoff Notes

- Completed: DAT_06.
- Remaining unfinished coverage: DAT_07 onward.
- Blocked or not applicable: none.
- State left for the next packet: staged source data remains outside watched import folder. 
