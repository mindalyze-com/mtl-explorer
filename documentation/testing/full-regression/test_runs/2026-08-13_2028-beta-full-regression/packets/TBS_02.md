# Packet: TBS_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: TBS_02.
- In scope: Track Browser search across all required field types.
- Out of scope: sorting, covered next.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_01.
- Required app/data state: paused filter exposing all twelve tracks.
- Required browser context: Statistics Tracks shared browser.

## Allowed Mutations

- Allowed: change the Track Browser search query.
- Not allowed: alter tracks or filters.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| TBS_02 | Searched by name, description, date, distance, duration, activity, and file name/path. | Each query matches the appropriate track and updates the visible summary. | All seven field types returned the expected single track with recalculated count, distance, and duration. | PASS | [cases](../assets/TBS_02-search-cases.txt), [file search](../assets/TBS_02-file-search.webp) |

## Issues

No issue found.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_02-search-cases.txt](../assets/TBS_02-search-cases.txt) | Exact query, result, and summary values. |
| [assets/TBS_02-file-search.webp](../assets/TBS_02-file-search.webp) | File-name search returning Track 100005. |

## Screenshot Evidence

The compact WebP shows the hidden file-field match reflected in the visible row and summary.

## Timings

| Step | Timing |
|---|---:|
| Each search update | < 1 s |

## Handoff Notes

- Completed: TBS_02 is terminal `PASS`.
- Remaining unfinished coverage: TBS_03 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: Statistics Tracks open; filter paused; file search active.
