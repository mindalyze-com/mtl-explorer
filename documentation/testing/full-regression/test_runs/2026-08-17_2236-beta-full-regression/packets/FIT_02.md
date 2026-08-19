# Packet: FIT_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FIT_02
- In scope: Upload/import acceptance, indexing, map display, browser search, and statistics inclusion.
- Out of scope: Full details-tab and download-content validation.

## Prerequisites

- Required previous coverage IDs or run packets: FIT_01.
- Required app/data state: Original FIT in watched folder.
- Required browser context: Admin Processing, freshness banner, Statistics Tracks, Track Details.

## Allowed Mutations

- Allowed: Wait for indexing, apply freshness Reload, search/open the new track.
- Not allowed: Replace the FIT with pre-converted GPX.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FIT_02 | Waited for all jobs, applied the in-app freshness Reload, searched `Activity` in Statistics Tracks, and opened the result. | FIT is accepted/indexed, visible on map, searchable in browser, and included in statistics. | GPS and follow-on jobs reached 6/6 without failure; map count increased 5 to 6; search returned one FIT-backed row with non-zero stats; opening it reached track 100005, labeled `Activity.fit`, with a mini-map. | PASS | [assets/FIT_02-index-display.txt](../assets/FIT_02-index-display.txt); [assets/FIT_01-copy.txt](../assets/FIT_01-copy.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FIT_02-index-display.txt](../assets/FIT_02-index-display.txt) | Index, freshness, map, search, statistics, ID, and detail evidence. |
| [assets/FIT_01-copy.txt](../assets/FIT_01-copy.txt) | Original checksum and watcher mutation. |

## Screenshot Evidence

Blocked by ACC_04; direct DOM and route evidence is recorded.

## Timings

| Step | Timing |
|---|---:|
| FIT indexing and settled jobs | Under 20 s |
| Freshness reload and user-facing verification | About 14 s |

## Handoff Notes

- Completed: FIT-backed track 100005 is indexed and present across required surfaces.
- Remaining unfinished coverage: None for FIT_02.
- Blocked or not applicable: Screenshot capture blocked under ACC_04.
- State left for the next packet: Track 100005 Overview open.
