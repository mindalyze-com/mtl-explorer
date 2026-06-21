# Packet: TRD_13

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: TRD_13
- In scope: Verify related tracks show duplicates plus previous/next tracks, and clicking a related track navigates to that track.
- Out of scope: Related-track calculation correctness beyond visible categories and navigation behavior.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_12, MAP_09 synthetic overlap imports.
- Required app/data state: Track #100022 available with related previous/next/duplicate entries.
- Required browser context: authenticated desktop detail page.

## Allowed Mutations

- Allowed: Navigate between detail pages by clicking related cards.
- Not allowed: Change track metadata or import/delete files.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_13 | Opened Track #100022, selected Related, verified Previous Tracks, Current Track, Next Tracks, and Duplicates sections, then clicked a visible related card. | Related tracks show duplicate and previous/next groups; clicking a related track navigates to its detail page. | Track #100022 showed previous, next, and duplicate sections. Clicking a related card navigated to `/mtl/track/100017`, whose detail page loaded with Related selected. | PASS | [assets/TRD_13-related-navigation.txt](../assets/TRD_13-related-navigation.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_13-related-navigation.txt](../assets/TRD_13-related-navigation.txt) | Related tab category and navigation evidence. |

## Screenshot Evidence

No screenshot asset was captured for this packet; direct DOM and URL evidence are recorded in the text asset.

## Timings

| Step | Timing |
|---|---:|
| Related tracks navigation check | ~5 min |

## Handoff Notes

- Completed: TRD_13.
- Remaining unfinished coverage: TRD_14 onward.
- Blocked or not applicable: none.
- State left for the next packet: Browser is on Track #100017 Related tab; dataset unchanged.
