# Packet: FLT_13

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: FLT_13.
- In scope: unavailable selected-category display and removal.
- Out of scope: selection clearing when changing filter views, covered next.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_12.
- Required app/data state: exact 2010 and 2013 selection with the range narrowed to 2010.
- Required browser context: Filter settings and Included categories sheet.

## Allowed Mutations

- Allowed: remove the unavailable 2013 selection and apply it.
- Not allowed: change the remaining 2010 selection.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FLT_13 | Narrowed the year range so selected 2013 had no matches, inspected the missing row, unchecked it, applied, and reopened the category sheet. | The unavailable selected category remains visible and removable; after removal it is absent while valid selection remains. | 2013 appeared checked with zero matches and a missing-state message. Removing and applying it left only checked 2010 with two matches. | PASS | [state](../assets/FLT_13-unavailable-category.txt), [final sheet](../assets/FLT_13-unavailable-removed.webp) |

## Issues

No issue found.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_13-unavailable-category.txt](../assets/FLT_13-unavailable-category.txt) | Exact before/after selection state. |
| [assets/FLT_13-unavailable-removed.webp](../assets/FLT_13-unavailable-removed.webp) | Final category sheet with only 2010 selected. |

## Screenshot Evidence

The compact WebP shows the final category sheet after removing the unavailable selection.

## Timings

| Step | Timing |
|---|---:|
| Inspect missing state | < 1 s |
| Apply removal | < 1 s |

## Handoff Notes

- Completed: FLT_13 is terminal `PASS`.
- Remaining unfinished coverage: FLT_14 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: Included categories open; exact 2010 selected; year range 2010-2010; result 2/12.
