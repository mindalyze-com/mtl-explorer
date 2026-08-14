# Packet: FLT_21

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: FLT_21.
- In scope: Filter and nested Review tracks standard sheet dimensions and detents.
- Out of scope: general Track Browser coverage, covered next.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_20.
- Required app/data state: exact one-track result with Review tracks available.
- Required browser context: 1280×720 desktop and 390×844 mobile.

## Allowed Mutations

- Allowed: open nested review, change viewport, toggle the sheet handle between detents.
- Not allowed: fullscreen or unrelated sheet resizing.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FLT_21 | Measured Filter and nested review on desktop, then toggled review between both mobile detents and checked content. | Both use standard Filter dimensions; nested review does not become oversized and remains usable at both mobile detents. | Desktop bounds matched exactly at 920×634. Mobile review worked at 736 px and 801.8 px heights and returned cleanly to the first detent. | PASS | [layout](../assets/FLT_21-sheet-layout.txt), [standard detent](../assets/FLT_21-mobile-standard.webp), [large detent](../assets/FLT_21-mobile-large.webp) |

## Issues

No issue found.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_21-sheet-layout.txt](../assets/FLT_21-sheet-layout.txt) | Exact desktop bounds and both mobile detent dimensions. |
| [assets/FLT_21-mobile-standard.webp](../assets/FLT_21-mobile-standard.webp) | Nested review at the standard mobile detent. |
| [assets/FLT_21-mobile-large.webp](../assets/FLT_21-mobile-large.webp) | Nested review at the 95vh mobile detent. |

## Screenshot Evidence

Both usable mobile detents are retained as compact WebP screenshots.

## Timings

| Step | Timing |
|---|---:|
| Detent transition | < 1 s each |
| Responsive reset | < 1 s |

## Handoff Notes

- Completed: FLT_21 is terminal `PASS`.
- Remaining unfinished coverage: TBS_01 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: desktop restored; nested Review tracks still open; exact WALKING result.
