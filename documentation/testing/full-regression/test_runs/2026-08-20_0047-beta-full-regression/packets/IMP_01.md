# Packet: IMP_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: IMP_01
- In scope: Baseline map count, track-browser count, statistics totals, freshness token, and GPS indexer status.
- Out of scope: Importing files.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_08 generation complete; watched GPS folder still empty.
- Required app/data state: Fresh empty database and no files in `data/gpx`.
- Required browser context: Signed-in desktop browser.

## Allowed Mutations

- Allowed: Read-only navigation through Map, Statistics, and Admin.
- Not allowed: Upload/copy/import before all baseline fields are captured.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| IMP_01 | Read map count; open Statistics Overview/Tracks; open Admin Overview, Data status technical details, and Processing. | Empty baseline values and current revisions/indexer state are captured before import. | Map and browser show 0 tracks, stats are empty, freshness token has index/media/geometry/tracks revisions at 0, and GPS is done 0/0. | PASS | [assets/IMP_01-baseline.txt](../assets/IMP_01-baseline.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/IMP_01-baseline.txt](../assets/IMP_01-baseline.txt) | Complete user-facing empty baseline and freshness/indexer identity. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; direct browser DOM evidence is transcribed in the linked asset.

## Timings

| Step | Timing |
|---|---:|
| Map/Stats/Admin baseline pass | 2 min |

## Handoff Notes

- Completed: All required baseline values captured before import.
- Remaining unfinished coverage: None for IMP_01.
- Blocked or not applicable: Screenshot asset only; direct visible-state evidence is complete.
- State left for the next packet: GPS/MEDIA indexers idle at 0/0; safe to import the five GPX files.
