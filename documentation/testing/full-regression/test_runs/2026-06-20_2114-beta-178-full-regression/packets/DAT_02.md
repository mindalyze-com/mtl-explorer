# Packet: DAT_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: DAT_02
- In scope: Prefer timestamped GPX trackpoints for duration, speed, moving time, and period statistics verification.
- Out of scope: App statistics verification; covered by IMP and TBS packets.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_01.
- Required app/data state: five public GPX files staged and parsed.
- Required browser context: none.

## Allowed Mutations

- Allowed: review parsed GPX metadata.
- Not allowed: replace files with non-timestamped private fixtures.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DAT_02 | Reviewed parsed timestamp counts for the five selected public GPX files. | Prefer GPX files with timestamped trackpoints so time-derived statistics can be verified. | PASS: all five selected GPX files have timestamp counts equal to their `trkpt` counts. | PASS | [assets/DAT_public-data-metadata.txt](../assets/DAT_public-data-metadata.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DAT_public-data-metadata.txt](../assets/DAT_public-data-metadata.txt) | Shows timestamp counts for each GPX file. |

## Screenshot Evidence

Not applicable; this is a data-source validation check.

## Timings

| Step | Timing |
|---|---:|
| Timestamp count review | <1 minute |

## Handoff Notes

- Completed: DAT_02 is terminal.
- Remaining unfinished coverage: DAT_03 onward.
- Blocked or not applicable: none.
- State left for the next packet: timestamped GPX files remain staged for import.
