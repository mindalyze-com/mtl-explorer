# Packet: FLT_16

> **FIX FOLLOW-UP — 2026-08-14: FIXED AND VERIFIED.** The original beta failure below is retained as run history. See [follow-up evidence](../fix-verification.md#resolution-matrix).

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: FLT_16.
- In scope: temporary map-legend hiding, Statistics isolation, and reset on global category change.
- Out of scope: first-time guidance, covered next.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_15.
- Required app/data state: Tracks by quarter with exact Q1 selected.
- Required browser context: map legend, Filter categories, and Statistics.

## Allowed Mutations

- Allowed: hide Q1 on the map, change global categories to All, and restore with Show all.
- Not allowed: change imported data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FLT_16 | Hid Q1 with the map legend, checked Statistics, then changed global categories from exact Q1 to All. | Legend hiding changes only map visibility; global category reload resets temporary hiding. | Map-only hiding passed: map became 0/12 while Statistics stayed 8. After global selection changed to 12, Statistics changed to 12 but the map stayed 4/12 with Q1 hidden. | FAIL | [state](../assets/FLT_16-legend-reset.txt), [defect](../assets/FLT_16-hidden-not-reset.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| FLT-16-P2 | P2 | A global Result categories change does not reset temporary map-legend hiding. | Select exact Q1; hide Q1 on the map; change global categories to All; Apply. | Map reload clears temporary hiding and shows all 12 globally selected tracks. | Filter and Statistics show 12, but map stays at 4/12 with Q1 marked hidden until Show all is used. | [state](../assets/FLT_16-legend-reset.txt), [defect](../assets/FLT_16-hidden-not-reset.webp) | Map can silently omit tracks after a global filter change, disagreeing with Filter and Statistics. |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_16-legend-reset.txt](../assets/FLT_16-legend-reset.txt) | Exact map, Filter, and Statistics counts through the flow. |
| [assets/FLT_16-hidden-not-reset.webp](../assets/FLT_16-hidden-not-reset.webp) | Mismatched 4/12 map and 12-track global result with one group hidden. |

## Screenshot Evidence

The WebP shows the persisted temporary hide after the global category change.

## Timings

| Step | Timing |
|---|---:|
| Temporary hide | < 1 s |
| Global category apply | < 1 s |

## Handoff Notes

- Completed: FLT_16 is terminal `FAIL` with FLT-16-P2.
- Remaining unfinished coverage: FLT_17 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: All quarter categories selected; map visibility restored; 12 tracks.
