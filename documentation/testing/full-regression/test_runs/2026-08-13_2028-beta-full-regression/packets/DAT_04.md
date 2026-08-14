# Packet: DAT_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: DAT_04.
- In scope: use the suggested verified public GPX repository and listed raw examples.
- Out of scope: import behavior.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_01, DAT_02.
- Required app/data state: five staged public GPX files.
- Required browser context: none.

## Allowed Mutations

- Allowed: compare recorded source URLs to the frozen-plan suggestions.
- Not allowed: replace the frozen-plan queue or source list.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DAT_04 | Compared each staged raw URL and filename with the five suggested `gps-touring/sample-gpx` examples in the frozen plan. | The suggested verified GPX source may be used for the positive import set. | All five staged files are the exact suggested raw examples from the public repository and passed the DAT_01/DAT_02 content checks. | PASS | [assets/DAT_01-public-gpx.txt](../assets/DAT_01-public-gpx.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DAT_01-public-gpx.txt](../assets/DAT_01-public-gpx.txt) | Exact suggested raw URLs, filenames, and validated GPX content. |

## Screenshot Evidence

Not applicable; this is public-source selection evidence.

## Timings

| Step | Timing |
|---|---:|
| Source comparison | < 1 s |

## Handoff Notes

- Completed: exact frozen-plan suggested GPX set is in use.
- Remaining unfinished coverage: DAT_05 onward and deferred DAT_03 fields.
- Blocked or not applicable: none.
- State left for the next packet: public GPX files remain staged outside the watched folder.
