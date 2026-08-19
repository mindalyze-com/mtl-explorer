# Packet: ACC_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: ACC_04
- In scope: Compact screenshots for representative working user-facing functions and failures.
- Out of scope: Invented or non-UI visuals.

## Prerequisites

- Required previous coverage IDs or run packets: ACC_03.
- Required app/data state: Fresh app reachable and browser tab controllable.
- Required browser context: Selected end-user browser at desktop viewport.

## Allowed Mutations

- Allowed: Capture read-only screenshots.
- Not allowed: Substitute fabricated visuals for browser screenshots.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ACC_04 | Attempted four direct screenshots on the signed-out login page across default/fixed viewports and fresh tabs, after reading the browser screenshot and troubleshooting guidance. | Compact screenshots can be captured for working and failing functions throughout the run. | Every direct screenshot call timed out in `Page.captureScreenshot`, while DOM inspection and interaction continued to work. No authentic browser screenshot could be saved. This is a tooling constraint and blocks the screenshot-evidence requirement, but not DOM-based functional testing. | BLOCKED | [assets/ACC_04-screenshot-capability.txt](../assets/ACC_04-screenshot-capability.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/ACC_04-screenshot-capability.txt](../assets/ACC_04-screenshot-capability.txt) | Screenshot attempts, exact constraint, and unblock path. |

## Screenshot Evidence

Blocked: the selected browser's screenshot operation timed out on every direct attempt.

## Timings

| Step | Timing |
|---|---:|
| Four screenshot attempts | About 85 s total |

## Handoff Notes

- Completed: Direct screenshot-capability attempts and diagnostic recording.
- Remaining unfinished coverage: None; terminally blocked in this environment.
- Blocked or not applicable: Requires a working screenshot operation or user-approved alternate browser surface.
- State left for the next packet: Browser interaction remains functional at 1280 x 720; screenshots unavailable.
