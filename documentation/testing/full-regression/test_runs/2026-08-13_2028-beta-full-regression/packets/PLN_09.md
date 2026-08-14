# Packet: PLN_09

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: PLN_09.
- In scope: clear missing-segment downloading/unavailable states.
- Out of scope: preservation of the old plan, covered next.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_08.
- Required app/data state: loaded Zürich plan and disposable BRouter segments.
- Required browser context: Planner centered on Reykjavík.

## Allowed Mutations

- Allowed: remove one exact disposable sidecar segment, request routing, let normal auto-download restore it.
- Not allowed: change unrelated routing data or services.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| PLN_09 | Recreated a missing W25_N60 segment and requested a Reykjavík route. | UI reports segment downloading or unavailable clearly instead of an unhandled error. | UI showed explicit downloading with auto-retry, then a clear unavailable message. Segment was automatically restored. | PASS | [state](../assets/PLN_09-routing-data-state.txt), [downloading](../assets/PLN_09-segment-downloading.webp) |

## Issues

No issue found.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/PLN_09-routing-data-state.txt](../assets/PLN_09-routing-data-state.txt) | Exact disposable setup, UI messages, retry, and segment recovery. |
| [assets/PLN_09-segment-downloading.webp](../assets/PLN_09-segment-downloading.webp) | End-user downloading message in Planner. |

## Screenshot Evidence

The compact WebP shows the clear in-app missing-data state.

## Timings

| Step | Timing |
|---|---:|
| Downloading message | < 1 s after route request |
| Auto-retry | 8 s |

## Handoff Notes

- Completed: PLN_09 is terminal `PASS`.
- Remaining unfinished coverage: PLN_10 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: failed cross-region waypoint still in history; old 4-leg route remains displayed; W25_N60 restored.
