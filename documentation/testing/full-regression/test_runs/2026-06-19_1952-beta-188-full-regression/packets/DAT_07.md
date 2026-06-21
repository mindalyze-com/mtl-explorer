# Packet: DAT_07

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md` required data-change section.
- Coverage ID or run packet: DAT_07
- In scope: Repeatable two-point segment data for measure/comparison/virtual-race checks.
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
| DAT_07 | Generated two fully synthetic anonymized GPX tracks crossing the same small coordinate corridor. | Measure/comparison/virtual-race checks have at least one repeatable segment with two or more tracks crossing the same two zones. | Two synthetic shared-zone GPX tracks are staged outside import folder for later import/use in MCT and AVR checks. | PASS | [assets/DAT_07-synthetic-shared-zone.txt](../assets/DAT_07-synthetic-shared-zone.txt); [assets/DAT_03-source-manifest.txt](../assets/DAT_03-source-manifest.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DAT_07-synthetic-shared-zone.txt](../assets/DAT_07-synthetic-shared-zone.txt) | Evidence for DAT_07. |
| [assets/DAT_03-source-manifest.txt](../assets/DAT_03-source-manifest.txt) | Evidence for DAT_07. |

## Screenshot Evidence

No screenshot required for this data-staging packet.

## Timings

| Step | Timing |
|---|---:|
| Data staging/metadata check | <1 min |

## Handoff Notes

- Completed: DAT_07.
- Remaining unfinished coverage: IMP_01 onward.
- Blocked or not applicable: none.
- State left for the next packet: staged source data remains outside watched import folder. 
