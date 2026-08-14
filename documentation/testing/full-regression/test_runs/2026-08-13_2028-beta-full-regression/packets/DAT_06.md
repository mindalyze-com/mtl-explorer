# Packet: DAT_06

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: DAT_06.
- In scope: ensure waypoint-only GPX and non-GPS FIT files are excluded from positive evidence.
- Out of scope: optional negative-file product behavior, which may be exercised with Admin upload coverage.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_01, DAT_02, DAT_05.
- Required app/data state: validated positive staging set.
- Required browser context: none.

## Allowed Mutations

- Allowed: audit the positive source set.
- Not allowed: count a non-track or non-GPS fixture toward the required imports.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DAT_06 | Audited the five-GPX and FIT positive set against direct content validation. | Non-GPS FIT and waypoint-only GPX files are not counted as positive evidence. | Every GPX positive has real, timestamped trackpoints and the FIT positive has 3,601 timestamped GPS records. No non-track/non-GPS file is included in positive import, count, or stats expectations. | PASS | [assets/DAT_06-positive-set.txt](../assets/DAT_06-positive-set.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DAT_06-positive-set.txt](../assets/DAT_06-positive-set.txt) | Explicit positive set and exclusion rule. |

## Screenshot Evidence

Not applicable; this is fixture qualification evidence.

## Timings

| Step | Timing |
|---|---:|
| Positive-set audit | < 1 s |

## Handoff Notes

- Completed: all positive fixtures are GPS track/activity data; invalid classes are excluded.
- Remaining unfinished coverage: DAT_07 onward and deferred DAT_03 mappings.
- Blocked or not applicable: none.
- State left for the next packet: six validated positive source files remain staged.
