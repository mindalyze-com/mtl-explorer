# Packet: TBS_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: TBS_01.
- In scope: Track Browser field coverage for the active filtered set.
- Out of scope: search matching, covered next.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_21.
- Required app/data state: exact WALKING filter with Track 100005.
- Required browser context: shared Review tracks browser.

## Allowed Mutations

- Allowed: inspect the current row and responsive card.
- Not allowed: change track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| TBS_01 | Inspected the filtered browser row on desktop and its responsive mobile card. | Browser lists all filtered tracks with name, date, distance, duration, activity, and supporting fields. | The one-track result showed every required field plus speed, energy, exploration, and import date in table and card layouts. | PASS | [row](../assets/TBS_01-filtered-row.txt), [desktop browser](../assets/FLT_20-stats-tracks.webp), [mobile card](../assets/FLT_20-review-mobile.webp) |

## Issues

No issue found.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_01-filtered-row.txt](../assets/TBS_01-filtered-row.txt) | Exact displayed field values. |
| [assets/FLT_20-stats-tracks.webp](../assets/FLT_20-stats-tracks.webp) | Desktop shared table. |
| [assets/FLT_20-review-mobile.webp](../assets/FLT_20-review-mobile.webp) | Narrow shared card. |

## Screenshot Evidence

The previous shared-browser screenshots directly show both layouts used by this packet.

## Timings

| Step | Timing |
|---|---:|
| Browser render | < 1 s |

## Handoff Notes

- Completed: TBS_01 is terminal `PASS`.
- Remaining unfinished coverage: TBS_02 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: desktop nested Review tracks open; exact WALKING result.
