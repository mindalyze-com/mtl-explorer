# Packet: NET_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: NET_01
- In scope: Installed-PWA offline reload with cached tracks and tiles.

## Prerequisites

- Required previous coverage IDs or run packets: MOB_06.
- Required app/data state: Online app loaded once.
- Required browser context: Installed PWA/web-app mode.

## Allowed Mutations

- Allowed: Identify the active client mode.
- Not allowed: Claim a normal tab as installed-PWA evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| NET_01 | Identified the active client as a normal in-app browser tab and applied the frozen applicability rule. | Installed PWA reloads offline with cached tracks/tiles. | This run has no installed PWA/web-app context; the frozen coverage text explicitly excludes normal browser-tab offline reload. | NOT APPLICABLE | [assets/NET_01-mode.txt](../assets/NET_01-mode.txt) |

## Issues

No issue; this is an explicit mode limitation in the frozen plan.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/NET_01-mode.txt](../assets/NET_01-mode.txt) | Client mode and applicability rule. |

## Screenshot Evidence

Not required for the mode determination.

## Timings

| Step | Timing |
|---|---:|
| Client-mode check | Under 1 min |

## Handoff Notes

- Completed: Applicability decision.
- Remaining unfinished coverage: None for NET_01.
- Blocked or not applicable: Installed-PWA-only offline reload is NOT APPLICABLE.
- State left for the next packet: Online normal browser tab.
