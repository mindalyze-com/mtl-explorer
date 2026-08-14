# Packet: FLT_19

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: FLT_19.
- In scope: Apply filter pause/resume synchronization and persistence on desktop and mobile.
- Out of scope: shared Review tracks features, covered next.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_18.
- Required app/data state: exact WALKING result with one of twelve tracks.
- Required browser context: 390×844 and default 1280×720 viewports.

## Allowed Mutations

- Allowed: pause/resume, reload while paused, navigate to Statistics.
- Not allowed: edit the retained filter setup during pause checks.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FLT_19 | Paused and resumed the one-track filter on mobile and desktop; reloaded while paused; checked map, Statistics, persistence, switch count and position. | Status, map, Statistics, persisted setup and switch remain synchronized; no duplicate/header switch. | Both widths changed cleanly between 1 and 12 tracks. Paused state survived reload with the exact setup retained. One switch was visible in Current result and no duplicate appeared. | PASS | [state](../assets/FLT_19-pause-resume.txt), [mobile resumed](../assets/FLT_19-mobile-resumed.webp), [desktop paused](../assets/FLT_19-desktop-paused.webp) |

## Issues

No issue found.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_19-pause-resume.txt](../assets/FLT_19-pause-resume.txt) | Exact status, counts, persistence, and switch states on both widths. |
| [assets/FLT_19-mobile-resumed.webp](../assets/FLT_19-mobile-resumed.webp) | Active one-track result at narrow mobile width. |
| [assets/FLT_19-desktop-paused.webp](../assets/FLT_19-desktop-paused.webp) | Paused all-track result on desktop. |

## Screenshot Evidence

Compact screenshots cover active mobile and paused desktop states.

## Timings

| Step | Timing |
|---|---:|
| Pause/resume transition | < 1 s each |
| Paused reload | < 2 s |

## Handoff Notes

- Completed: FLT_19 is terminal `PASS`.
- Remaining unfinished coverage: FLT_20 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: desktop Filter open; exact WALKING active; one matching track.
