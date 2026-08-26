# Packet: MED_31

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_31
- In scope: Viewer rendering and behavior at 390 × 760 and 375 × 667.
- Out of scope: Desktop viewer behavior, covered by MED_30.

## Prerequisites

- Required previous coverage IDs or run packets: MED_30.
- Required app/data state: Six-photo activity viewer baseline.
- Required browser context: Authenticated phone-sized browser at both frozen dimensions.

## Allowed Mutations

- Allowed: Ephemeral viewer, Details, Nearby, and scroll state.
- Not allowed: App/data mutations or changing the required frozen dimensions.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_31 | Confirmed the required sizes, measured the only connected browser viewport, checked its responsive breakpoint, and inspected whether a viewport/device control was available. | Exercise both phone sizes end to end: photo-first initial state, native scrollable dark Details sheet through every action, compact Nearby row, and clean bottom edge. | The only connected browser is fixed at 1049 × 942 and does not match the phone breakpoint. Its browser capability has no viewport/device-emulation control, and no second connected device exists. Static responsive code is present but cannot establish the required rendered results, so neither frozen phone size can be tested end to end. | BLOCKED | [assets/MED_31-viewport-constraint.txt](../assets/MED_31-viewport-constraint.txt) |

## Issues

- None. This is an environment/tooling block, not a product finding.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_31-viewport-constraint.txt](../assets/MED_31-viewport-constraint.txt) | Exact required/available dimensions, breakpoint result, and capability limitation. |

## Screenshot Evidence

The available 1049 × 942 screenshot cannot represent either required phone size. No valid phone screenshot can be captured in this environment.

## Timings

| Step | Timing |
|---|---:|
| Viewport measurement and capability check | Under 1 s |

## Handoff Notes

- Completed: Exact viewport/capability diagnosis and responsive-source orientation.
- Remaining unfinished coverage: None; MED_31 is terminal BLOCKED.
- Blocked or not applicable: Both frozen phone viewport executions are blocked by the connected browser's fixed desktop viewport.
- State left for the next packet: Root main map with 8 Tracks; data baseline unchanged.
