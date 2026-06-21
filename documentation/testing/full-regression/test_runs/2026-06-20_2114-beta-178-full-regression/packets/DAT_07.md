# Packet: DAT_07

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: DAT_07
- In scope: Prepare repeatable two-point segment data with two or more tracks crossing the same zones.
- Out of scope: Importing or using the synthetic tracks in measure/comparison/virtual-race UI; covered by later MCT/AVR packets.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_01 through DAT_06.
- Required app/data state: source-data staging directory exists outside watched import folder.
- Required browser context: none.

## Allowed Mutations

- Allowed: generate fully synthetic anonymized GPX tracks outside the watched import folder.
- Not allowed: use private local GPX tracks as fixtures or evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DAT_07 | Generated two fully synthetic anonymized GPX tracks crossing the same small coordinate corridor and copied them to the target source-data directory. | Measure/comparison/virtual-race checks have at least one repeatable two-point segment with two or more tracks crossing the same two zones. | PASS: `synthetic-shared-zone-a.gpx` and `synthetic-shared-zone-b.gpx` each contain 18 timestamped trackpoints crossing the same start/end zones and are staged outside the watched import folder. | PASS | [assets/DAT_07-synthetic-shared-zone.txt](../assets/DAT_07-synthetic-shared-zone.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DAT_07-synthetic-shared-zone.txt](../assets/DAT_07-synthetic-shared-zone.txt) | Synthetic shared-zone GPX filenames, checksums, sizes, and corridor description. |

## Screenshot Evidence

Not applicable; this is a data-source staging check.

## Timings

| Step | Timing |
|---|---:|
| Synthetic GPX generation and target staging | <1 minute |

## Handoff Notes

- Completed: DAT_07 is terminal.
- Remaining unfinished coverage: IMP_01 onward, plus DAT_03 imported mappings after import.
- Blocked or not applicable: none.
- State left for the next packet: public and synthetic source files remain staged outside the watched import folder.
