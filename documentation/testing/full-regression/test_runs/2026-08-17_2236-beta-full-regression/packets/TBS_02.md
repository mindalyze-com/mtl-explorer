# Packet: TBS_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TBS_02
- In scope: Track-browser search across name, description, date, distance, duration, activity, and source file metadata.
- Out of scope: Sort order and quick-view subsets covered by TBS_03/TBS_04.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_01.
- Required app/data state: Statistics Tracks All view with 15 resolved tracks.
- Required browser context: Desktop track browser with clear search.

## Allowed Mutations

- Allowed: Enter and replace search queries.
- Not allowed: Modify tracks or filters.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TBS_02 | Queried an exact/unique value for each required search field and read matching rows plus visible summaries. | Names, descriptions, dates, distances, durations, activities, and file paths all match. | Every field matched the expected row(s), including Activity.fit and JuraRoute72011.gpx. Summary count/distance/duration followed each result, and clearing restored 15 rows. | PASS | [assets/TBS_02-file-search.txt](../assets/TBS_02-file-search.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_02-file-search.txt](../assets/TBS_02-file-search.txt) | Search matrix, matched rows, summary totals, historical observation, and final clear state. |

## Screenshot Evidence

Unavailable under ACC_04. Exact queries, row texts, match counts, and summary values provide direct DOM evidence.

## Timings

| Step | Timing |
|---|---:|
| Eight-query search matrix | About 4 s |
| Clear and baseline verification | Under 1 s |

## Handoff Notes

- Completed: Search coverage for every required visible and metadata field.
- Remaining unfinished coverage: None for TBS_02.
- Blocked or not applicable: None.
- State left for the next packet: Statistics Tracks All view remains open; search is empty and all 15 tracks are restored.
