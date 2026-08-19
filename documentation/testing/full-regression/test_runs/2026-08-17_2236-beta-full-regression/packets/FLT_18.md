# Packet: FLT_18

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FLT_18
- In scope: Returning-user compact guidance on desktop/mobile, overflow, Read more without Important, Back, and Close.
- Out of scope: First-use automatic guidance covered by FLT_17.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_17.
- Required app/data state: Alternate-origin context after Got it.
- Required browser context: Desktop plus temporary 390x844 viewport tab.

## Allowed Mutations

- Allowed: Open/close help and temporarily override viewport.
- Not allowed: Leave a viewport override or alternate-origin tab active.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FLT_18 | Opened returning Filter on desktop and 390x844 mobile, measured guidance/card overflow, opened Read more, used Back, then Close. | Guidance stays compact without overflow; Read more has no Important; Back returns and Close closes. | Desktop and mobile had zero horizontal overflow. Returning help omitted Important/Got it. Back returned to Filter and Close removed the sheet on both viewports. | PASS | [assets/FLT_18-returning-guidance.txt](../assets/FLT_18-returning-guidance.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_18-returning-guidance.txt](../assets/FLT_18-returning-guidance.txt) | Desktop/mobile dimensions, overflow metrics, help/badge state, Back/Close, and cleanup. |

## Screenshot Evidence

Unavailable under ACC_04. Exact viewport, element rects, scroll/client dimensions, rendered actions, and route/sheet states provide direct DOM evidence.

## Timings

| Step | Timing |
|---|---:|
| Desktop returning flow | About 3 s |
| Mobile returning flow | About 4 s |
| Viewport/tab cleanup | Under 1 s |

## Handoff Notes

- Completed: Returning guidance behavior at desktop and narrow mobile, including cleanup.
- Remaining unfinished coverage: None for FLT_18.
- Blocked or not applicable: None.
- State left for the next packet: Primary IP-origin tab remains active; viewport is reset; no alternate-origin tabs remain.
