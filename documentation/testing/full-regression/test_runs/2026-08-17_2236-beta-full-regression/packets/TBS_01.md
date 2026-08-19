# Packet: TBS_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TBS_01
- In scope: Track-browser list population and displayed track fields.
- Out of scope: Search, sorting, quick views, and row navigation covered by following TBS packets.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_20.
- Required app/data state: Reset filter with 15 visible tracks.
- Required browser context: Statistics → Tracks → All on desktop.

## Allowed Mutations

- Allowed: Read list content.
- Not allowed: Change tracks, filters, quick views, or sorting.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TBS_01 | Counted track rows and read headers plus representative Bicycle/Walking, short/long, and imported records. | All resolved tracks list with name, date, distance, duration, activity, and related values. | All 15 tracks were listed. Rows exposed shape, start, name/description, activity, distance, duration, speed, energy, exploration, and imported time. | PASS | [assets/TBS_01-track-list.txt](../assets/TBS_01-track-list.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_01-track-list.txt](../assets/TBS_01-track-list.txt) | Exact row count, columns, totals, and representative row values. |

## Screenshot Evidence

Unavailable under ACC_04. Exact table headers and extracted row values provide direct DOM evidence.

## Timings

| Step | Timing |
|---|---:|
| List inspection | Under 1 s |

## Handoff Notes

- Completed: All-track list and field coverage.
- Remaining unfinished coverage: None for TBS_01.
- Blocked or not applicable: None.
- State left for the next packet: Statistics Tracks All view remains open with clear search and 15 rows.
