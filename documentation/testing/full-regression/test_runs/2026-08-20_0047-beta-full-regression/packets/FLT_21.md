# Packet: FLT_21

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FLT_21
- In scope: Standard Filter/Review sheet dimensions, nested-sheet sizing, and both mobile detents.
- Out of scope: Track-browser content, covered by FLT_20 and TBS packets.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_20.
- Required app/data state: Filter and Review sheets available.
- Required browser context: Authenticated in-app browser without geometry/viewport capabilities.

## Allowed Mutations

- Allowed: Open, maximize, restore, and nest sheets.
- Not allowed: Infer visual dimensions from DOM structure alone.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FLT_21 | Inspect Filter/Review dimensions and both mobile detents. | Standard dimensions; no oversized jump; both detents usable. | Sheet controls and nested content worked structurally, but visual dimensions and mobile detents cannot be observed with the connected browser. | BLOCKED | [assets/FLT_21-sheet-detents.txt](../assets/FLT_21-sheet-detents.txt); [packets/ACC_04.md](ACC_04.md) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_21-sheet-detents.txt](../assets/FLT_21-sheet-detents.txt) | Structural checks and exact visual/viewport blocker. |

## Screenshot Evidence

Screenshot capture, element geometry, and narrow viewport emulation are BLOCKED in ACC_04.

## Timings

| Step | Timing |
|---|---:|
| Desktop sheet-control inspection | 2 min |
| Capability audit | 1 min |

## Handoff Notes

- Completed: Structural maximize/restore and nested Review checks.
- Remaining unfinished coverage: None; visual dimensions and mobile detents are terminal BLOCKED.
- Blocked or not applicable: Exact desktop sizing and both mobile detents (ACC_04).
- State left for the next packet: Filter Review open on the eight-track result.
