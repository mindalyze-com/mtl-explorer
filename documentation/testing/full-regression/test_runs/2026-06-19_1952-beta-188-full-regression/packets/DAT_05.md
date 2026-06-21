# Packet: DAT_05

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md` required data-change section.
- Coverage ID or run packet: DAT_05
- In scope: One public GPS-bearing FIT activity file.
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
| DAT_05 | Downloaded Garmin FIT JavaScript SDK `Activity.fit` from the regression-plan suggested public URL and recorded checksum/size. | At least one public FIT activity file with GPS positions is available for FIT import/conversion coverage. | `Activity.fit` is staged with size 94,096 bytes and SHA-256 `949a238e...d591387`; import/display evidence is reserved for FIT packets. | PASS | [assets/DAT_05-fit-file.txt](../assets/DAT_05-fit-file.txt); [assets/DAT_03-source-manifest.txt](../assets/DAT_03-source-manifest.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DAT_05-fit-file.txt](../assets/DAT_05-fit-file.txt) | Evidence for DAT_05. |
| [assets/DAT_03-source-manifest.txt](../assets/DAT_03-source-manifest.txt) | Evidence for DAT_05. |

## Screenshot Evidence

No screenshot required for this data-staging packet.

## Timings

| Step | Timing |
|---|---:|
| Data staging/metadata check | <1 min |

## Handoff Notes

- Completed: DAT_05.
- Remaining unfinished coverage: DAT_06 onward.
- Blocked or not applicable: none.
- State left for the next packet: staged source data remains outside watched import folder. 
