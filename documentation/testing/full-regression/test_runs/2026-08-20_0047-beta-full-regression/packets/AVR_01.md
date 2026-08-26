# Packet: AVR_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: AVR_01
- In scope: Desktop/mobile Animate lifecycle, map-first transport, pause/resume/stop/expand/reset, date range, duration, and speed.
- Out of scope: Multi-racer virtual race.

## Prerequisites

- Required previous coverage IDs or run packets: MCT_06.
- Required app/data state: Eight active tracks.
- Required browser context: Connected desktop browser; mobile/touch context requested by coverage.

## Allowed Mutations

- Allowed: Adjust playback sliders and replay/stop/reset animation.
- Not allowed: Claim desktop pointer as mobile evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| AVR_01 | Exercise full desktop Animate lifecycle and audit mobile availability. | Flow works on desktop/mobile and retains pre-play tracks. | Desktop controls all passed, including stable pause and map-first transport. Fixed browser has no mobile/touch context, and ACC_04 blocks pixel proof that pre-play canvas tracks remain visible. | BLOCKED | [assets/AVR_01-animation-lifecycle.txt](../assets/AVR_01-animation-lifecycle.txt) |

## Issues

None. Terminal block is test-environment coverage, not a product defect.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/AVR_01-animation-lifecycle.txt](../assets/AVR_01-animation-lifecycle.txt) | Desktop controls, progress states, and mobile/visual constraints. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; mobile viewport is also unavailable.

## Timings

| Step | Timing |
|---|---:|
| Settings/date/speed checks | 2 min |
| Play/pause/resume/stop | 2 min |
| Complete/expand/reset | 1 min |

## Handoff Notes

- Completed: Full desktop animation lifecycle.
- Remaining unfinished coverage: None; mobile and pre-play canvas proof are terminal BLOCKED.
- Blocked or not applicable: Mobile/touch context; screenshot-only line-retention evidence.
- State left for the next packet: Animate reset to ready, very-fast speed selected.
