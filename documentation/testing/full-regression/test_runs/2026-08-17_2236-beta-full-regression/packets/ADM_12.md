# Packet: ADM_12

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: ADM_12
- In scope: Direct Admin URLs, history, mobile Back, and sheet-close routing.
- Out of scope: Content-specific Admin actions.

## Prerequisites

- Required previous coverage IDs or run packets: ADM_11.
- Required app/data state: Authenticated, healthy 14-track map.
- Required browser context: Desktop plus temporary 390 x 844 viewport.

## Allowed Mutations

- Allowed: Direct route navigation, browser history, mobile Back, and Close.
- Not allowed: Change server data or leave the mobile viewport active.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ADM_12 | Used direct System/Preferences URLs, Back/Forward, then direct mobile Server log, Back to overview, and Admin Close. | Section and map routes remain synchronized in every path. | Direct routes and history restored matching headings; mobile Back produced `/admin` Overview; Close produced the 14-track `/mtl/` map. | PASS | [assets/ADM_12-routing.txt](../assets/ADM_12-routing.txt); [assets/ADM_12-mobile-back.jpg](../assets/ADM_12-mobile-back.jpg) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/ADM_12-routing.txt](../assets/ADM_12-routing.txt) | Exact direct, history, mobile Back, Close, and viewport results. |
| [assets/ADM_12-mobile-back.jpg](../assets/ADM_12-mobile-back.jpg) | True 390 x 844 Server log with Back to overview visible. |

## Screenshot Evidence

- The portrait capture preserves the direct Server log section and its mobile
  Back action at the tested 390 x 844 viewport.

## Timings

| Step | Timing |
|---|---:|
| Desktop direct routes and history | About 3 s |
| Mobile direct route, Back, and Close | About 3 s |

## Handoff Notes

- Completed: All Admin route synchronization paths passed.
- Remaining unfinished coverage: None for ADM_12.
- Blocked or not applicable: None.
- State left for the next packet: Viewport reset to desktop; authenticated map
  remains at 14 Tracks. A freshness banner from helper-setting writes may remain.
