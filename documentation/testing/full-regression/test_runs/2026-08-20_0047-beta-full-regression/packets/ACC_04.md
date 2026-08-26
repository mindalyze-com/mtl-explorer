# Packet: ACC_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: ACC_04
- In scope: Compact screenshots for representative passing functions and failures.
- Out of scope: Replacing direct functional evidence with screenshots alone.

## Prerequisites

- Required previous coverage IDs or run packets: ACC_03.
- Required app/data state: Running app with visible login, map, and About states.
- Required browser context: Connected browser with screenshot capability.

## Allowed Mutations

- Allowed: Capture compact browser screenshots and recompress them to WebP.
- Not allowed: Capture unrelated user windows or private data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ACC_04 | Attempt viewport and full-page screenshots on two in-app browser tabs, including after enabling visibility; check for an alternate connected browser; try an OS window-only capture. | Representative passing and failing UI states can be saved as compact WebP assets. | Browser interaction and DOM evidence work, but every browser screenshot attempt returns `Unable to capture screenshot`; only the in-app browser is connected, and OS window capture is not permitted. | BLOCKED | [assets/ACC_04-screenshot-block.txt](../assets/ACC_04-screenshot-block.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/ACC_04-screenshot-block.txt](../assets/ACC_04-screenshot-block.txt) | Screenshot attempts, exact failure, and unblock paths. |

## Screenshot Evidence

None: screenshot capture is the blocked capability being recorded.

## Timings

| Step | Timing |
|---|---:|
| Screenshot troubleshooting | 6 min |

## Handoff Notes

- Completed: Exhausted the connected browser's documented screenshot path and safe window-only fallback.
- Remaining unfinished coverage: None; this packet is terminal BLOCKED for this run.
- Blocked or not applicable: Screenshot capture is blocked by the current browser/OS capability. A connected extension browser or restored screenshot permission would unblock it.
- State left for the next packet: Functional UI checks continue with DOM, console, network, download, and command evidence.
