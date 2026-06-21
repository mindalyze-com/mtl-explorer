# Packet: NET_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: NET_01
- In scope: Installed PWA / installed web-app offline reload behavior.
- Out of scope: Normal browser-tab offline behavior.

## Prerequisites

- Required previous coverage IDs or run packets: MOB_05.
- Required app/data state: MTL Explorer reachable in browser.
- Required browser context: Installed standalone PWA/web-app mode.

## Allowed Mutations

- Allowed: Inspect browser display mode and service-worker/PWA runtime context.
- Not allowed: Force an installed-PWA-only offline reload in a normal browser tab.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| NET_01 | Checked the active browser runtime before attempting the installed-PWA offline reload row. | Installed web-app mode is available before testing offline reload. | The run used normal browser contexts: `display-mode: browser=true`, standalone/minimal-ui false. The plan explicitly says normal browser-tab runs should mark this row not applicable unless the app is installed. | NOT APPLICABLE | [assets/NET_01-pwa-context.txt](../assets/NET_01-pwa-context.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/NET_01-pwa-context.txt](../assets/NET_01-pwa-context.txt) | Installed-PWA applicability check. |

## Screenshot Evidence

No screenshot evidence is required for this applicability-only packet.

## Timings

| Step | Timing |
|---|---:|
| PWA/display-mode inspection | 3.9 s |

## Handoff Notes

- Completed: NET_01 marked not applicable for this normal-browser run.
- Remaining unfinished coverage: NET_02 onward at packet creation time.
- Blocked or not applicable: Installed PWA/web-app mode was not available.
- State left for the next packet: No data mutation.
