# Packet: MCT_06

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: MCT_06.
- In scope: geographic sanity of comparison geometry.
- Out of scope: segment distance correctness, recorded by MCT_05.

## Prerequisites

- Required previous coverage IDs or run packets: MCT_05.
- Required app/data state: selected A-B Lannion segment in Compare.
- Required browser context: desktop comparison map.

## Allowed Mutations

- Allowed: visually inspect the selected geometry and map extent.
- Not allowed: change zones or track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MCT_06 | Inspected the complete A-B geometry, markers, local labels, and scale in the comparison map. | Segment line stays inside the selected tracks' real local bounds with no global/off-continent jump. | The whole line and A/B markers stayed within a 50 m-scale Lannion view; no `[0,0]`, South Africa, ocean, or global extent appeared. | PASS | [local map](../assets/MCT_06-local-bounds.webp), [bounds check](../assets/MCT_06-local-bounds.txt) |

## Issues

No new issue. The independent zero-distance extraction is `MCT-05-P1`.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MCT_06-local-bounds.webp](../assets/MCT_06-local-bounds.webp) | Entire selected line within Lannion landmarks. |
| [assets/MCT_06-local-bounds.txt](../assets/MCT_06-local-bounds.txt) | Exact geographic sanity observations. |

## Screenshot Evidence

![Local comparison geometry](../assets/MCT_06-local-bounds.webp)

## Timings

| Step | Timing |
|---|---:|
| Bounds inspection | < 1 s after settle |

## Handoff Notes

- Completed: MCT_06 is terminal `PASS`.
- Remaining unfinished coverage: AVR_01 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: comparison overlay open on A-B; map remains centered on Lannion.
