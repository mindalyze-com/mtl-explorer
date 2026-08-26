# Packet: FLT_07

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FLT_07
- In scope: Active legend, group hide/show, and collapse/expand.
- Out of scope: Statistics invariance under legend-only hiding, covered by FLT_16.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_06.
- Required app/data state: One-track ON_FOOT keyword result.
- Required browser context: Main map with no sheet overlay.

## Allowed Mutations

- Allowed: Temporarily hide the one legend group and collapse the legend.
- Not allowed: Leave the group hidden.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FLT_07 | Hide ON_FOOT, collapse/expand legend, then show ON_FOOT. | Legend reflects the filter and visibility changes affect map immediately. | Hide changed map-visible count 1 -> 0 and exposed hidden state; collapse removed rows; restore returned count to 1 with ON_FOOT shown. | PASS | [assets/FLT_07-legend.txt](../assets/FLT_07-legend.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_07-legend.txt](../assets/FLT_07-legend.txt) | Before/hide/collapse/restore legend and count states. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible legend states are linked above.

## Timings

| Step | Timing |
|---|---:|
| Hide, collapse, expand, and restore | 3 min |

## Handoff Notes

- Completed: Legend reflects active filter; group visibility and collapse work.
- Remaining unfinished coverage: None for FLT_07.
- Blocked or not applicable: None.
- State left for the next packet: Legend expanded; ON_FOOT shown; one-track filtered result.
