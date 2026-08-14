# Packet: MOB_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: MOB_04.
- In scope: Planner waypoint tap, drag, and insert with touch.

## Prerequisites

- Required previous coverage IDs or run packets: MOB_03 and PLN_11.
- Required app/data state: mobile Planner route previously executed.
- Required browser context: 390 x 844 pointer-only browser.

## Allowed Mutations

- Allowed: reuse direct PLN_11 mobile pointer evidence and re-audit capabilities.
- Not allowed: equate pointer with native touch.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MOB_04 | Reconciled the direct mobile Planner waypoint test and rechecked current browser input capabilities. | Waypoints can be tapped, dragged, and inserted with touch. | Pointer drag/reroute worked in PLN_11, but the harness exposes no touch events, so touch tap/drag/insert cannot execute or be attributed. | BLOCKED | [capability](../assets/MOB_04-touch.txt), [Planner touch packet](PLN_11.md), [mobile pointer result](../assets/PLN_11-mobile-drag.txt) |

## Issues

No product issue created; missing touch capability prevents attribution.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MOB_04-touch.txt](../assets/MOB_04-touch.txt) | Constraint and unblock path. |
| [packets/PLN_11.md](PLN_11.md) | Direct mobile Planner pointer execution. |
| [assets/PLN_11-mobile-drag.txt](../assets/PLN_11-mobile-drag.txt) | Mobile Planner pointer result and cleanup. |

## Screenshot Evidence

No screenshot was created for the pointer-only result; the exact reroute and cleanup values are in the linked text evidence.

## Timings

| Step | Timing |
|---|---:|
| Capability audit | < 0.1 s |

## Handoff Notes

- Completed: MOB_04 is terminal `BLOCKED`.
- Remaining unfinished coverage: MOB_05 onward.
- Blocked or not applicable: requires a native touch-capable harness.
- State left for the next packet: 390 x 844 map.
