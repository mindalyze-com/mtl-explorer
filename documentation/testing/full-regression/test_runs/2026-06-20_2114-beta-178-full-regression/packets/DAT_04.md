# Packet: DAT_04

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: DAT_04
- In scope: Use the suggested verified GPX source when possible.
- Out of scope: Import verification; covered by IMP packets.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_01 through DAT_03.
- Required app/data state: GPX files staged and metadata recorded.
- Required browser context: none.

## Allowed Mutations

- Allowed: review staged source URLs.
- Not allowed: substitute private files.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DAT_04 | Reviewed the selected GPX source URLs. | Prefer the suggested `gps-touring/sample-gpx` source and raw files. | PASS: all five selected public GPX files are from the suggested `gps-touring/sample-gpx` GitHub raw URLs listed in the test plan. | PASS | [assets/DAT_public-data-metadata.txt](../assets/DAT_public-data-metadata.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DAT_public-data-metadata.txt](../assets/DAT_public-data-metadata.txt) | Shows each selected GPX source URL. |

## Screenshot Evidence

Not applicable; this is a data-source validation check.

## Timings

| Step | Timing |
|---|---:|
| Source URL review | <1 minute |

## Handoff Notes

- Completed: DAT_04 is terminal.
- Remaining unfinished coverage: DAT_05 onward, plus DAT_03 imported mappings after import.
- Blocked or not applicable: none.
- State left for the next packet: staged GPX source set remains unchanged.
