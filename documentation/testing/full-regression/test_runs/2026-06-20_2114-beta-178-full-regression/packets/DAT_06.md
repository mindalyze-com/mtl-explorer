# Packet: DAT_06

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: DAT_06
- In scope: Ensure positive evidence excludes non-GPS FIT files and waypoint-only GPX files.
- Out of scope: Negative import testing for unsupported or waypoint-only files.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_01 through DAT_05.
- Required app/data state: public positive evidence files staged.
- Required browser context: none.

## Allowed Mutations

- Allowed: audit staged positive evidence set.
- Not allowed: treat waypoint-only GPX or non-GPS FIT as passing import evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DAT_06 | Audited the staged positive evidence set. | Non-GPS FIT files and waypoint-only GPX files are not counted as positive evidence. | PASS: the five GPX files all have real `trkpt` sequences, and the FIT file has GPS latitude/longitude records; no waypoint-only or non-GPS files are counted as positive evidence. | PASS | [assets/DAT_public-data-metadata.txt](../assets/DAT_public-data-metadata.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DAT_public-data-metadata.txt](../assets/DAT_public-data-metadata.txt) | Parsed proof that positive files contain GPX trackpoints or FIT GPS records. |

## Screenshot Evidence

Not applicable; this is a data-source validation check.

## Timings

| Step | Timing |
|---|---:|
| Positive evidence audit | <1 minute |

## Handoff Notes

- Completed: DAT_06 is terminal.
- Remaining unfinished coverage: DAT_07 onward, plus DAT_03 imported mappings after import.
- Blocked or not applicable: none.
- State left for the next packet: positive data set remains staged outside the watched import folder.
