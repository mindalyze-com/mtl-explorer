# Packet: DAT_04

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md` required data-change section.
- Coverage ID or run packet: DAT_04
- In scope: Use the suggested verified GPX source.
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
| DAT_04 | Verified the staged public GPX files come from the regression-plan suggested gps-touring/sample-gpx raw URLs. | Suggested sample-gpx source is used or equivalent verified public GPX sources are recorded. | All five staged public GPX files use the exact suggested raw URLs from the test plan. | PASS | [assets/DAT_04-suggested-sources.txt](../assets/DAT_04-suggested-sources.txt); [assets/DAT_03-source-manifest.txt](../assets/DAT_03-source-manifest.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DAT_04-suggested-sources.txt](../assets/DAT_04-suggested-sources.txt) | Evidence for DAT_04. |
| [assets/DAT_03-source-manifest.txt](../assets/DAT_03-source-manifest.txt) | Evidence for DAT_04. |

## Screenshot Evidence

No screenshot required for this data-staging packet.

## Timings

| Step | Timing |
|---|---:|
| Data staging/metadata check | <1 min |

## Handoff Notes

- Completed: DAT_04.
- Remaining unfinished coverage: DAT_05 onward.
- Blocked or not applicable: none.
- State left for the next packet: staged source data remains outside watched import folder. 
