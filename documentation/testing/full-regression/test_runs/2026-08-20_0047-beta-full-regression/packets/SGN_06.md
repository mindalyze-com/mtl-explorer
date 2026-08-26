# Packet: SGN_06

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: SGN_06
- In scope: Verify the startup splash content and its removal after map/track load.
- Out of scope: Startup failure, covered by SGN_07.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_05.
- Required app/data state: Signed-in browser with nine active tracks; server healthy.
- Required browser context: Loaded map in the in-app browser.

## Allowed Mutations

- Allowed: Reload the signed-in app and inspect rendered splash elements.
- Not allowed: Change application data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_06 | Reload the signed-in root; capture the immediate and settled rendered states. | Splash logo, background, and message display during startup, then disappear once map and tracks load. | Immediate state had the MTL Explorer image, `Loading your trails`, a WebP photo background, and 0 tracks; after 1.9 s the curtain/backdrop were absent and the map showed 9 tracks. | PASS | [assets/SGN_06-splash.txt](../assets/SGN_06-splash.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SGN_06-splash.txt](../assets/SGN_06-splash.txt) | Immediate rendered splash attributes and settled map state. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; rendered DOM and style evidence are linked above.

## Timings

| Step | Timing |
|---|---:|
| Reload completion to immediate splash capture | <0.3 s |
| Immediate capture to settled verification | 1.9 s |

## Handoff Notes

- Completed: Branded startup splash and post-load disappearance.
- Remaining unfinished coverage: None for SGN_06.
- Blocked or not applicable: None.
- State left for the next packet: Browser signed in at loaded map.
