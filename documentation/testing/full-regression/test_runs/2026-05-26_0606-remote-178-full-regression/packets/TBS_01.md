# Packet: TBS_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: TBS_01
- In scope: track browser listing fields for all visible tracks.
- Out of scope: search and sorting behavior.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_08.
- Required app/data state: filtering off; 10 visible tracks.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: open Stats Tracks tab.
- Not allowed: edit or delete track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TBS_01 | Opened Stats and switched to Tracks. | Track browser lists all tracks with name, date, distance, duration, activity, and related fields. | Tracks tab listed 10 tracks with Start, Track, Activity, Distance, Duration, Avg km/h, Energy, Exploration, Imported, summary totals, sort buttons, and pagination. | PASS | `assets/TBS_01-track-browser-list.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/TBS_01-track-browser-list.webp | Track browser listing with columns and 10-track summary. |

## Timings

| Step | Timing |
|---|---:|
| Track browser listing check | <2m |

## Handoff Notes

- Completed: TBS_01 terminal PASS.
- Remaining unfinished coverage: continue with TBS_02.
- Blocked or not applicable: none.
- State left for the next packet: Stats Tracks tab open, filtering off.
