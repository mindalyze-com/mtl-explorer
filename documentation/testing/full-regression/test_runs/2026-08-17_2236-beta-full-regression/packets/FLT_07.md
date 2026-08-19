# Packet: FLT_07

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FLT_07
- In scope: Legend reflects active filter; collapse/reopen and category hide/show update visible map result immediately.
- Out of scope: Statistics independence from legend hiding covered by FLT_16.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_06.
- Required app/data state: Two-result keyword filter and 5 Colors palette.
- Required browser context: Main map legend.

## Allowed Mutations

- Allowed: Collapse/reopen legend and temporarily hide/show CYCLING.
- Not allowed: Leave any category hidden.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FLT_07 | Inspected category labels/counts/colors, collapsed/reopened legend, hid CYCLING, and restored it. | Legend reflects the filter; collapse/hide controls update the map immediately. | Two correct categories rendered. Collapse removed/reopen restored their controls. Hiding CYCLING changed the control to Show and visible count 2→1 immediately; restoring changed 1→2 with no reload/error. | PASS | [assets/FLT_07-legend.txt](../assets/FLT_07-legend.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_07-legend.txt](../assets/FLT_07-legend.txt) | Legend contents, RGB colors, collapse state, hide/show names, counts, and restoration. |

## Screenshot Evidence

Unavailable under ACC_04. Exact rendered labels, RGB swatches, pressed states, and visible counts provide direct DOM evidence.

## Timings

| Step | Timing |
|---|---:|
| Collapse/reopen | Under 1 s |
| Hide/show and count propagation | Under 1 s each |

## Handoff Notes

- Completed: Active legend contents, collapse/reopen, category hide/show, and restoration.
- Remaining unfinished coverage: None for FLT_07.
- Blocked or not applicable: None.
- State left for the next packet: Main map with two matched/visible tracks and both legend categories shown.
