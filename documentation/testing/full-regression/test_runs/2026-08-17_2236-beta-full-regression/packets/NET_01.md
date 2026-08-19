# Packet: NET_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: NET_01
- In scope: Offline reload in installed PWA/web-app mode only.
- Out of scope: A normal browser-tab offline reload.

## Prerequisites

- Required previous coverage IDs or run packets: MOB_06.
- Required app/data state: App loaded online once.
- Required browser context: Installed standalone web app required by the coverage text.

## Allowed Mutations

- Allowed: Read-only display-mode and service-worker inspection.
- Not allowed: Disconnect the remote server for a normal-tab test that the plan excludes.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| NET_01 | Inspected display modes, manifest presence, browser context, and service-worker registrations. | Only an installed PWA/web app is required to reload offline with cached tracks and tiles. | The test runs in a normal in-app browser tab: standalone/fullscreen/minimal-ui are false and there are zero registrations. The frozen plan explicitly excludes this context from the offline reload requirement. | NOT APPLICABLE | [assets/NET_01-install-mode.txt](../assets/NET_01-install-mode.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/NET_01-install-mode.txt](../assets/NET_01-install-mode.txt) | Direct installed-mode and service-worker applicability evidence. |

## Screenshot Evidence

Not useful for a display-mode applicability result; text evidence records the queried state.

## Timings

| Step | Timing |
|---|---:|
| Applicability inspection | Under 0.1 seconds |

## Handoff Notes

- Completed: Installed-app applicability was directly checked.
- Remaining unfinished coverage: None for NET_01.
- Blocked or not applicable: NOT APPLICABLE because this is a normal browser tab, as the frozen plan permits.
- State left for the next packet: Online authenticated desktop session, no network mutation.
