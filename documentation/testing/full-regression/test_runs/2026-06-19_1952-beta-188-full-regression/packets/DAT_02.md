# Packet: DAT_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md` required data-change section.
- Coverage ID or run packet: DAT_02
- In scope: Timestamp preference for public GPX data.
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
| DAT_02 | Parsed timestamps for every trackpoint in the five public GPX files. | Prefer timestamped trackpoints so duration, speed, moving time, and period statistics can be verified. | All five public GPX files have timestamp counts equal to trackpoint counts. | PASS | [assets/DAT_02-timestamp-counts.txt](../assets/DAT_02-timestamp-counts.txt); [assets/DAT_03-source-manifest.txt](../assets/DAT_03-source-manifest.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DAT_02-timestamp-counts.txt](../assets/DAT_02-timestamp-counts.txt) | Evidence for DAT_02. |
| [assets/DAT_03-source-manifest.txt](../assets/DAT_03-source-manifest.txt) | Evidence for DAT_02. |

## Screenshot Evidence

No screenshot required for this data-staging packet.

## Timings

| Step | Timing |
|---|---:|
| Data staging/metadata check | <1 min |

## Handoff Notes

- Completed: DAT_02.
- Remaining unfinished coverage: DAT_03 onward.
- Blocked or not applicable: none.
- State left for the next packet: staged source data remains outside watched import folder. 
