# Packet: APP_05

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: APP_05
- In scope: First visible frame during a Dark hard refresh.
- Out of scope: General reload persistence, covered by APP_04.

## Prerequisites

- Required previous coverage IDs or run packets: APP_04.
- Required app/data state: Persisted Dark preference.
- Required browser context: Authenticated dark root map.

## Allowed Mutations

- Allowed: One page-level reload and read-only high-frequency sampling.
- Not allowed: Inject styles, scripts, or alter the stored theme.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| APP_05 | Reloaded in Dark, sampled first accessible document states every ~20 ms, and captured the first screenshot frame. | Dark refresh does not flash Light first. | The only pre-content sample was empty/transparent; first content at 20 ms was dark and all later samples stayed dark. First captured frame was the dark startup image. | PASS | [assets/APP_05-first-paint.txt](../assets/APP_05-first-paint.txt); [assets/APP_05-first-frame.jpg](../assets/APP_05-first-frame.jpg) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/APP_05-first-paint.txt](../assets/APP_05-first-paint.txt) | Sampling method and exact first-content state. |
| [assets/APP_05-first-frame.jpg](../assets/APP_05-first-frame.jpg) | First captured dark startup frame. |

## Screenshot Evidence

- The first capture shows a darkened mountain startup image and white loading
  identity; no light application panel is present.

## Timings

| Step | Timing |
|---|---:|
| First meaningful dark content | About 20 ms after sampling began |

## Handoff Notes

- Completed: Hard refresh did not expose a light application frame.
- Remaining unfinished coverage: None for APP_05.
- Blocked or not applicable: None.
- State left for the next packet: Dark root map is reloading/settling after the sampled refresh.
