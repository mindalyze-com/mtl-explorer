# Packet: FLT_20

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: FLT_20.
- In scope: Filter Review tracks shared browser and Statistics Tracks recheck.
- Out of scope: Filter and nested-review sheet dimensions, covered next.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_19.
- Required app/data state: exact one-track WALKING result.
- Required browser context: desktop and 390×844 responsive viewport.

## Allowed Mutations

- Allowed: search, sort, page controls, select the result, open/close details, switch responsive width, navigate to Statistics Tracks.
- Not allowed: change the filtered dataset.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FLT_20 | Exercised Review tracks search, summary, responsive table/card, sorting, pagination, result selection and details; then rechecked Statistics Tracks. | Filter review and Statistics Tracks use the same complete responsive browser behavior. | The shared browser passed at both widths. Selection opened #100005 details and retained search on return. Statistics Tracks showed the same one-row controls and table. | PASS | [checks](../assets/FLT_20-shared-browser.txt), [mobile review](../assets/FLT_20-review-mobile.webp), [Statistics Tracks](../assets/FLT_20-stats-tracks.webp) |

## Issues

No issue found.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_20-shared-browser.txt](../assets/FLT_20-shared-browser.txt) | Complete shared-browser functional and responsive checks. |
| [assets/FLT_20-review-mobile.webp](../assets/FLT_20-review-mobile.webp) | Compact Review tracks card layout. |
| [assets/FLT_20-stats-tracks.webp](../assets/FLT_20-stats-tracks.webp) | Shared desktop browser in Statistics Tracks. |

## Screenshot Evidence

Mobile card and desktop Statistics Tracks screenshots show both shared layouts.

## Timings

| Step | Timing |
|---|---:|
| Search/sort changes | < 1 s each |
| Details open/close | < 1 s each |
| Responsive transition | < 1 s |

## Handoff Notes

- Completed: FLT_20 is terminal `PASS`.
- Remaining unfinished coverage: FLT_21 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: desktop Statistics Tracks open; exact WALKING active; one track.
