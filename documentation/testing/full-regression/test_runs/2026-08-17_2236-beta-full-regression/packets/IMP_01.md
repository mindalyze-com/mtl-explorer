# Packet: IMP_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: IMP_01
- In scope: Baseline map/browser/statistics counts, freshness token, and GPS indexer status.
- Out of scope: Import mutation.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_08; no watched imports.
- Required app/data state: Fresh empty database and `data/gpx/` empty.
- Required browser context: Signed-in desktop browser.

## Allowed Mutations

- Allowed: Read-only navigation among Map, Statistics, and Admin.
- Not allowed: Add files before baseline completes.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| IMP_01 | Read the map count, Statistics Overview, Statistics Tracks summary, Admin Processing, and expanded Admin Data status technical details before import. | A complete empty-database baseline is recorded before the five GPX files enter the watcher. | Map/browser count 0; distance 0.00 m; duration 0m 00s; GPS indexer done at 0/0; data in sync with index/tracks/geometry/media all r0 and revision sum 97. | PASS | [assets/IMP_01-baseline.txt](../assets/IMP_01-baseline.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/IMP_01-baseline.txt](../assets/IMP_01-baseline.txt) | Exact empty map/browser/stats/indexer/freshness baseline. |

## Screenshot Evidence

Blocked by ACC_04; direct DOM evidence is recorded.

## Timings

| Step | Timing |
|---|---:|
| Desktop baseline navigation | About 25 s |

## Handoff Notes

- Completed: Full pre-import baseline.
- Remaining unfinished coverage: None for IMP_01.
- Blocked or not applicable: Screenshot capture blocked under ACC_04.
- State left for the next packet: Browser remains in Admin Data status; filesystem watcher still has zero files.
