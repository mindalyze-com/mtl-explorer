# Packet: FLT_14

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: FLT_14.
- In scope: result-category selection behavior when reselecting or changing filter views.
- Out of scope: exact-selection reload restoration, covered next.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_13.
- Required app/data state: Tracks by year with exact 2010 category selected.
- Required browser context: Filter view and Included categories sheets.

## Allowed Mutations

- Allowed: reselect Tracks by year, then switch to Tracks by quarter.
- Not allowed: manually change categories during the checks.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FLT_14 | Reselected the active Tracks by year view and then switched to Tracks by quarter, inspecting categories after each apply. | Same-view selection remains; changing to a different filter clears result-category selection. | Exact 2010 remained after same-view apply. Switching to quarter reset selection to All categories with Q1 and Q3 checked. | PASS | [state](../assets/FLT_14-view-selection.txt), [quarter categories](../assets/FLT_14-selection-reset.webp) |

## Issues

No issue found.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_14-view-selection.txt](../assets/FLT_14-view-selection.txt) | Exact before/after view and checkbox state. |
| [assets/FLT_14-selection-reset.webp](../assets/FLT_14-selection-reset.webp) | Category sheet after changing to quarter view. |

## Screenshot Evidence

The WebP shows the different-view result with All categories selected.

## Timings

| Step | Timing |
|---|---:|
| Same-view apply | < 1 s |
| Different-view apply | < 1 s |

## Handoff Notes

- Completed: FLT_14 is terminal `PASS`.
- Remaining unfinished coverage: FLT_15 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: Included categories open; Tracks by quarter; All categories selected; 12 matching tracks.
