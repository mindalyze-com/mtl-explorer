# Packet: ERR_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: ERR_02
- In scope: Rapid tool switching without stale markers, listeners, or cursors.

## Prerequisites

- Required previous coverage IDs or run packets: ERR_01, MCT_03, AVR_01, ACC_04.
- Required app/data state: Healthy eight-track map and all primary tools available.
- Required browser context: Signed-in warmed desktop session.

## Allowed Mutations

- Allowed: Open and replace tool panels repeatedly; close the final panel.
- Not allowed: Claim canvas/listener cleanup from DOM-only evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ERR_02 | Ran 21 rapid activations across all seven primary tools, closed the final Planner, audited accessible stale states, and checked console logs. | No previous markers, listeners, cursors, panels, or errors remain. | Routes/panels/alerts cleaned up, the eight-track map remained usable, and no console error appeared. Canvas markers/cursors and listener inventory cannot be inspected or captured in this browser. | BLOCKED | [assets/ERR_02-rapid-switch.txt](../assets/ERR_02-rapid-switch.txt); [assets/MCT_03-stop-cleanup.txt](../assets/MCT_03-stop-cleanup.txt); [assets/AVR_01-animation-lifecycle.txt](../assets/AVR_01-animation-lifecycle.txt) |

## Issues

No new product issue; the remaining requirement is a render/runtime-inspection constraint.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/ERR_02-rapid-switch.txt](../assets/ERR_02-rapid-switch.txt) | Three-cycle action matrix, final DOM, console, and constraint. |

## Screenshot Evidence

Blocked by ACC_04; the relevant cursor/marker surfaces are canvas-rendered.

## Timings

| Step | Timing |
|---|---:|
| Three 7-tool cycles | About 55 s including browser action settlement |
| Final close/audit | About 3.7 s |

## Handoff Notes

- Completed: Three-cycle rapid navigation, accessible cleanup, and console audit.
- Remaining unfinished coverage: None for ERR_02.
- Blocked or not applicable: Direct canvas marker/cursor and event-listener inventory.
- State left for the next packet: Clean root map; eight tracks; no open tool panel.
