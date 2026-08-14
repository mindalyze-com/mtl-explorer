# Packet: FLT_17

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: FLT_17.
- In scope: first-time Filter guidance in a clean browser context.
- Out of scope: returning desktop and mobile guidance layout, covered next.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_16.
- Required app/data state: normal 12-track dataset.
- Required browser context: fresh application origin without prior guidance acknowledgement.

## Allowed Mutations

- Allowed: sign in, open Filter, acknowledge with Got it, close, and reopen.
- Not allowed: alter server data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FLT_17 | Opened Filter first time in a clean context, inspected guidance and controls, clicked Got it, then reopened Filter. | Guidance auto-opens with Important badge, app-wide and map-color explanations, Back/Close; Got it prevents another automatic opening. | All content and controls were present. Got it returned to Filter, and the next opening showed the normal Current result instead of guidance. | PASS | [state](../assets/FLT_17-first-guidance.txt), [guidance](../assets/FLT_17-first-guidance.webp) |

## Issues

No issue found.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_17-first-guidance.txt](../assets/FLT_17-first-guidance.txt) | Exact clean-context guidance and acknowledgement behavior. |
| [assets/FLT_17-first-guidance.webp](../assets/FLT_17-first-guidance.webp) | First-time guidance page. |

## Screenshot Evidence

The WebP shows the full first-time guidance with the Important badge and Got it action.

## Timings

| Step | Timing |
|---|---:|
| First guidance open | < 1 s after Filter |
| Return after Got it | < 1 s |

## Handoff Notes

- Completed: FLT_17 is terminal `PASS`.
- Remaining unfinished coverage: FLT_18 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: returning-user Filter open on the clean alternate origin; 12 tracks.
