# Packet: DAT_05

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: DAT_05
- In scope: Use at least one public FIT activity file with GPS positions.
- Out of scope: App import/conversion verification; covered by FIT packets.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_01 through DAT_04.
- Required app/data state: public FIT file staged.
- Required browser context: none.

## Allowed Mutations

- Allowed: download and parse public FIT test file.
- Not allowed: count non-GPS FIT files as positive evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DAT_05 | Downloaded Garmin's public `Activity.fit` sample and parsed the FIT records for GPS latitude/longitude fields. | At least one public FIT activity file has GPS positions. | PASS: `Activity.fit` from `garmin/fit-javascript-sdk` has 3,601 record messages and all 3,601 contain GPS latitude/longitude fields; FIT integrity check returned true. | PASS | [assets/DAT_public-data-metadata.txt](../assets/DAT_public-data-metadata.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DAT_public-data-metadata.txt](../assets/DAT_public-data-metadata.txt) | FIT source URL, checksum, size, integrity, and GPS-bearing record count. |

## Screenshot Evidence

Not applicable; this is a data-source validation check.

## Timings

| Step | Timing |
|---|---:|
| FIT download and parser validation | <1 minute |

## Handoff Notes

- Completed: DAT_05 is terminal.
- Remaining unfinished coverage: DAT_06 onward, plus DAT_03 imported mappings after import.
- Blocked or not applicable: none.
- State left for the next packet: `Activity.fit` remains staged for FIT import/conversion checks.
