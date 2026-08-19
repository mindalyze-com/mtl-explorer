# Packet: FLT_21

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FLT_21
- In scope: Standard Filter and nested Review tracks sheet dimensions/detents on desktop and both mobile detents.
- Out of scope: Track-browser feature parity already covered by FLT_20.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_20 and deferred deletion packets.
- Required app/data state: Current 15-track result with retained imports.
- Required browser context: Desktop 1280 x 720 and temporary exact 390 x 844 mobile viewport.

## Allowed Mutations

- Allowed: Open/close Filter and Review, exact retained-name searches, pointer sheet drag, and temporary viewport override.
- Not allowed: Data, filter-result, or persisted settings changes.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FLT_21 | Measured desktop Filter overview, Filter detail, and nested Review shells; measured standard mobile Filter/Review partial detents; pointer-dragged Review to the full detent; searched a retained track at both mobile detents; checked overflow, console, reopen, and viewport reset. | Filter and Review use standard Filter dimensions/detents; Review does not jump to an oversized desktop sheet and stays usable at both mobile detents. | Desktop Review kept the standard 920 px Filter width and viewport bottom. At 390 x 844, Filter and settled Review matched at x=0/y=108/390x736; Review snapped to the full y=42.20/390x801.80 detent. Search/cards worked at both, no overflow/error appeared, reopen restored partial, and desktop sizing was restored. | PASS | [assets/FLT_21-sheet-detents.txt](../assets/FLT_21-sheet-detents.txt); [assets/FLT_21-mobile-review.webp](../assets/FLT_21-mobile-review.webp); [assets/FLT_21-mobile-review-full.webp](../assets/FLT_21-mobile-review-full.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_21-sheet-detents.txt](../assets/FLT_21-sheet-detents.txt) | Exact desktop/mobile shell rectangles, both detents, usability, overflow, console, and reset state. |
| [assets/FLT_21-mobile-review.webp](../assets/FLT_21-mobile-review.webp) | Review at the standard 390 x 844 partial detent with a live retained-track result. |
| [assets/FLT_21-mobile-review-full.webp](../assets/FLT_21-mobile-review-full.webp) | Same Review result at the full mobile detent. |

## Screenshot Evidence

![Review tracks at the standard mobile partial detent](../assets/FLT_21-mobile-review.webp)

![Review tracks at the full mobile detent](../assets/FLT_21-mobile-review-full.webp)

## Timings

| Step | Timing |
|---|---:|
| Mobile detent settle after pointer drag | Under 0.5 seconds |
| Search result refresh at each detent | Under 0.3 seconds |

## Handoff Notes

- Completed: Desktop dimensions, standard mobile partial detent, full mobile detent, usability, overflow, console, reopen, and viewport reset.
- Remaining unfinished coverage: None for FLT_21.
- Blocked or not applicable: Native touch was not needed; pointer detent behavior is directly covered here.
- State left for the next packet: Desktop 1280 x 720, no Filter/Review sheet open, current 15-track data.
