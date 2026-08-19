# Packet: MED_33

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_33
- In scope: 100,000-ID centered selection, bounded 200-thumbnail window, direct selection, bidirectional shifts, map/activity page-boundary loading, DOM/request counts, and timings.
- Out of scope: Small viewer behavior alone.

## Prerequisites

- Required previous coverage IDs or run packets: MED_32 and MED_21.
- Required app/data state: 100,000 media IDs and paged map/activity collections.
- Required browser context: Viewer centered away from both ends.

## Allowed Mutations

- Allowed: Viewer navigation only.
- Not allowed: Direct database scale seeding.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_33 | Counted the live expanded viewer DOM and audited the frozen data prerequisite. | At most 200 thumbnails while shifting both directions across map/activity pages on 100,000 IDs, with recorded requests/timings. | Current six-item viewer has exactly six buttons/thumbnails and one active image. No collection can reach a page boundary because the required 100,000-row fixture is absent. | BLOCKED | [assets/MED_33-bounded-window.txt](../assets/MED_33-bounded-window.txt) |

## Issues

- Missing MED_21 large-library fixture, not a small-dataset product failure.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_33-bounded-window.txt](../assets/MED_33-bounded-window.txt) | Exact current DOM counts and unavailable prerequisite. |

## Screenshot Evidence

- Not sufficient for the required performance assertion; exact DOM counts are recorded instead.

## Timings

| Step | Timing |
|---|---:|
| Six-item filmstrip expansion/count | Under 200 ms |

## Handoff Notes

- Completed: Small bounded baseline count.
- Remaining unfinished coverage: None for MED_33; missing scale branches are terminally blocked.
- Blocked or not applicable: 100,000-ID window, shifts, adjacent pages, and timings.
- State left for the next packet: Viewer normal size at photo 2/6 with Nearby expanded.
