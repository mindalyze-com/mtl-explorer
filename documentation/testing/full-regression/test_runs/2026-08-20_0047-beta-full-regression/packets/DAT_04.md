# Packet: DAT_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: DAT_04
- In scope: Use of the suggested verified public GPX source.
- Out of scope: Import results.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_01-DAT_03.
- Required app/data state: Staged public files.
- Required browser context: None.

## Allowed Mutations

- Allowed: Read and download the frozen plan's suggested public sources.
- Not allowed: Substitute private tracks.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DAT_04 | Fetch the five exact `gps-touring/sample-gpx` raw URLs suggested by the frozen plan and validate them. | Suggested verified GPX source is usable for the positive import flow. | All five suggested source files downloaded and passed XML/trackpoint validation. | PASS | [assets/DAT_04-suggested-source.txt](../assets/DAT_04-suggested-source.txt); [assets/DAT_01-public-gpx.txt](../assets/DAT_01-public-gpx.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DAT_04-suggested-source.txt](../assets/DAT_04-suggested-source.txt) | Exact suggested source paths used. |

## Screenshot Evidence

Not useful for public-source selection.

## Timings

| Step | Timing |
|---|---:|
| Source verification | Included in DAT_01 (<10 s) |

## Handoff Notes

- Completed: All five suggested GPX inputs were used.
- Remaining unfinished coverage: None for DAT_04.
- Blocked or not applicable: None.
- State left for the next packet: Positive GPX files remain in unwatched staging.
