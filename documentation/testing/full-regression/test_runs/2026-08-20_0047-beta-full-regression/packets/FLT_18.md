# Packet: FLT_18

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FLT_18
- In scope: Returning-user guidance on desktop and narrow viewport, including Read more, Important suppression, Back, and Close.
- Out of scope: Apply-filter pause state, covered by FLT_19.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_17.
- Required app/data state: First-visit guidance already dismissed.
- Required browser context: Isolated returning-user tab; viewport emulation unavailable.

## Allowed Mutations

- Allowed: Open manual guidance, use Back and Close, and reopen Filter.
- Not allowed: Claim a narrow-layout result without a narrow viewport.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FLT_18 | Open returning-user guidance on desktop and narrow viewport; use Read more, Back, and Close. | Guidance is compact, has no Important, and controls work at both widths. | Desktop passed completely; narrow viewport could not be established with the connected browser. | BLOCKED | [assets/FLT_18-returning-guidance.txt](../assets/FLT_18-returning-guidance.txt); [packets/ACC_04.md](ACC_04.md) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_18-returning-guidance.txt](../assets/FLT_18-returning-guidance.txt) | Desktop flow and exact narrow-viewport blocker. |

## Screenshot Evidence

Screenshot capture and narrow viewport emulation are BLOCKED in ACC_04.

## Timings

| Step | Timing |
|---|---:|
| Desktop returning-user guidance | 3 min |
| Narrow viewport capability audit | 1 min |

## Handoff Notes

- Completed: Desktop returning-user Read more, Important suppression, Back, Close, and reopen behavior.
- Remaining unfinished coverage: None; required narrow pass is terminal BLOCKED.
- Blocked or not applicable: Narrow viewport end-user validation (ACC_04).
- State left for the next packet: Isolated Filter overview open; main regression tab remains authenticated.
