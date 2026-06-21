# Packet: SGN_06

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: SGN_06
- In scope: Splash screen appears during startup and disappears after map/tracks load.
- Out of scope: startup failure retry; covered by SGN_07.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_02.
- Required app/data state: authenticated session and imported tracks.
- Required browser context: authenticated desktop browser context.

## Allowed Mutations

- Allowed: delay early API requests in the browser test context.
- Not allowed: stop or change the server.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_06 | Opened the app while delaying the first API requests, captured the startup state, then allowed the app to finish loading. | The splash screen with logo/background/message displays during startup and disappears once the map and tracks are loaded. | PASS: during delayed startup the page showed `LOADING YOUR TRAILS`, logo/brand-like content, and zero map canvases; after loading, the same URL showed two map canvases and the map/navigation controls. | PASS | [assets/SGN_06-splash-startup.txt](../assets/SGN_06-splash-startup.txt); [assets/SGN_06-splash.webp](../assets/SGN_06-splash.webp); [assets/SGN_06-map-loaded.webp](../assets/SGN_06-map-loaded.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SGN_06-splash-startup.txt](../assets/SGN_06-splash-startup.txt) | Delayed-startup and loaded-map text evidence. |
| [assets/SGN_06-splash.webp](../assets/SGN_06-splash.webp) | Startup splash screenshot. |
| [assets/SGN_06-map-loaded.webp](../assets/SGN_06-map-loaded.webp) | Loaded map screenshot after splash disappears. |

## Screenshot Evidence

![Startup splash](../assets/SGN_06-splash.webp)

![Map after startup](../assets/SGN_06-map-loaded.webp)

## Timings

| Step | Timing |
|---|---:|
| Delayed startup and map load | ~8 seconds |

## Handoff Notes

- Completed: SGN_06 is terminal.
- Remaining unfinished coverage: SGN_07 onward.
- Blocked or not applicable: none.
- State left for the next packet: no server mutations; only browser-context API delays were used.
