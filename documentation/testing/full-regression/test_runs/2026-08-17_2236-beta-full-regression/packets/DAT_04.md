# Packet: DAT_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: DAT_04
- In scope: Use the suggested verified `gps-touring/sample-gpx` files.
- Out of scope: Post-import product behavior.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_01-DAT_02.
- Required app/data state: Public fixture staging.
- Required browser context: None.

## Allowed Mutations

- Allowed: Download the exact suggested public raw files.
- Not allowed: Substitute private GPX data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DAT_04 | Downloaded the exact five raw file URLs suggested by the frozen plan from the public `gps-touring/sample-gpx` repository and verified their content. | Suggested verified public GPX source is usable for the run. | All five suggested URLs downloaded successfully and produced valid, timestamped track sequences. | PASS | [assets/DAT_01-public-source-preflight.txt](../assets/DAT_01-public-source-preflight.txt); [assets/DAT_03-source-manifest.txt](../assets/DAT_03-source-manifest.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DAT_01-public-source-preflight.txt](../assets/DAT_01-public-source-preflight.txt) | Exact suggested URLs and file verification. |
| [assets/DAT_03-source-manifest.txt](../assets/DAT_03-source-manifest.txt) | Public source page and license-note record. |

## Screenshot Evidence

Not applicable; this is public fixture-source verification.

## Timings

| Step | Timing |
|---|---:|
| Covered by DAT_01 download/preflight | 4.4 s |

## Handoff Notes

- Completed: Exact suggested public GPX source set verified.
- Remaining unfinished coverage: None for DAT_04.
- Blocked or not applicable: None.
- State left for the next packet: Public files staged outside the watcher.
