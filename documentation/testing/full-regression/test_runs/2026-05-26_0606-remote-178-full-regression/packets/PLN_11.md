# Packet: PLN_11

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: PLN_11
- In scope: planner touch dragging on mobile.
- Out of scope: desktop mouse waypoint drag, already covered by PLN_04.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_02, PLN_04.
- Required app/data state: planner available.
- Required browser context: mobile viewport with touch input enabled.

## Allowed Mutations

- Allowed: create/edit temporary route in a touch-capable mobile browser context.
- Not allowed: substitute desktop mouse dragging as touch evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| PLN_11 | Checked whether the current tooling can provide mobile viewport and touch input for planner dragging. | Touch dragging on mobile works for placing and moving waypoints. | BLOCKED: workspace has no Playwright/touch harness installed, and the in-app browser surface for this run does not expose mobile viewport or touch emulation controls. | BLOCKED | `assets/PLN_11-touch-blocked.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/PLN_11-touch-blocked.txt | Tooling constraint details. |

## Timings

| Step | Timing |
|---|---:|
| Tooling check | <2m |

## Handoff Notes

- Completed: PLN_11 terminal BLOCKED.
- Remaining unfinished coverage: continue with MCT_01.
- Blocked or not applicable: mobile/touch planner dragging requires a touch-capable test context.
- State left for the next packet: planner remains open; desktop planner workflow validated through PLN_10.
