# Packet: FLT_06

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FLT_06
- In scope: Live count, map color/legend, and Stats updates without reload.
- Out of scope: Legend-only visibility changes.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_03 and FLT_05.
- Required app/data state: Activities by keyword available.
- Required browser context: Same-run map, Filter, and Stats transition.

## Allowed Mutations

- Allowed: Reuse direct same-run FLT_03 live-update evidence.
- Not allowed: Treat a reload transition as live apply.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FLT_06 | Evaluate the no-reload 9 -> 1 -> 9 keyword transition across map, legend, and Stats. | All filter-aware surfaces update without full reload. | Visible count, result label, color legend, and Stats totals synchronized on both edit and clear without reload. | PASS | [assets/FLT_06-live-update.txt](../assets/FLT_06-live-update.txt); [assets/FLT_03-auto-apply.txt](../assets/FLT_03-auto-apply.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_06-live-update.txt](../assets/FLT_06-live-update.txt) | FLT acceptance mapping. |
| [assets/FLT_03-auto-apply.txt](../assets/FLT_03-auto-apply.txt) | Direct edit/clear cross-view observations. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible live states are linked above.

## Timings

| Step | Timing |
|---|---:|
| Evidence mapping from completed same-run transition | <1 min |

## Handoff Notes

- Completed: Live count, legend/color, and Stats synchronization.
- Remaining unfinished coverage: None for FLT_06.
- Blocked or not applicable: None.
- State left for the next packet: Activities by keyword active; date and Media keyword retained.
