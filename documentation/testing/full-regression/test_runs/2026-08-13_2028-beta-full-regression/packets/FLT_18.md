# Packet: FLT_18

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: FLT_18.
- In scope: returning-user guidance on desktop and narrow mobile.
- Out of scope: Apply filter pause/resume, covered next.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_17.
- Required app/data state: guidance already acknowledged.
- Required browser context: desktop and 390×844 responsive viewport.

## Allowed Mutations

- Allowed: resize viewport, open Read more, use Back and Close.
- Not allowed: reset first-time acknowledgement.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FLT_18 | Inspected returning guidance at 1280×720 and 390×844, opened Read more, used Back, and closed the mobile sheet. | Guidance remains compact without overflow; full page has no Important badge; Back and Close work. | Desktop and mobile cards fit exactly without overflow. Full guidance had no Important badge; Back returned and Close closed the sheet. | PASS | [layout](../assets/FLT_18-returning-guidance.txt), [desktop](../assets/FLT_18-returning-desktop.webp), [mobile](../assets/FLT_18-returning-mobile.webp) |

## Issues

No issue found.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_18-returning-guidance.txt](../assets/FLT_18-returning-guidance.txt) | Desktop/mobile dimensions, overflow, and navigation results. |
| [assets/FLT_18-returning-desktop.webp](../assets/FLT_18-returning-desktop.webp) | Returning desktop full guidance. |
| [assets/FLT_18-returning-mobile.webp](../assets/FLT_18-returning-mobile.webp) | Returning narrow-mobile full guidance. |

## Screenshot Evidence

Both responsive layouts are retained as compact WebP screenshots.

## Timings

| Step | Timing |
|---|---:|
| Desktop Read more | < 1 s |
| Mobile Read more | < 1 s |

## Handoff Notes

- Completed: FLT_18 is terminal `PASS`.
- Remaining unfinished coverage: FLT_19 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: Filter closed at 390×844 on the returning-user origin; 12 tracks.
