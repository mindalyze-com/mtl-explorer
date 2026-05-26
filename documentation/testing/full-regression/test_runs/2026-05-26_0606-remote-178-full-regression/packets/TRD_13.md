# Packet: TRD_13

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: TRD_13
- In scope: related-track groups for previous/next/duplicates and navigation from a related item.
- Out of scope: recalculating duplicate scores.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_01, TRD_02.
- Required app/data state: imported GPX/FIT/format test tracks are indexed; track #100001 is open.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: click related-track entries.
- Not allowed: edit track metadata or source files.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_13 | Opened track #100001, switched to Related, verified related groups, then clicked related entry `Track #100005`. | Related tracks show duplicates and previous/next tracks; clicking one navigates to that track. | The Related tab showed Next Tracks and Duplicates for #100001. Clicking `Track #100005` navigated the detail panel to #100005 and refreshed related context. | PASS | `assets/TRD_13-related-fullscreen.webp`, `assets/TRD_13-related-navigated.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/TRD_13-related-fullscreen.webp | Related tab showing next tracks and duplicates for #100001. |
| assets/TRD_13-related-navigated.webp | Detail panel after clicking related `Track #100005`. |

## Timings

| Step | Timing |
|---|---:|
| Related tab navigation | <2m |

## Handoff Notes

- Completed: TRD_13 terminal PASS.
- Remaining unfinished coverage: continue with TRD_14.
- Blocked or not applicable: none.
- State left for the next packet: detail panel open on #100005 Related tab.
