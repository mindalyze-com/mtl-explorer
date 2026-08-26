# Packet: MED_34

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_34
- In scope: Viewer default, independence, persistence, surfaces, focus, and interactions in light/dark application and viewer themes across map, activity, and Statistics entry points.
- Out of scope: General viewer interaction already covered by MED_30/32; here it is repeated specifically in dark viewer mode.

## Prerequisites

- Required previous coverage IDs or run packets: MED_33 exact cleanup.
- Required app/data state: Eight-item media baseline.
- Required browser context: Authenticated disposable browser whose local state can be cleared through the visible Session flow.

## Allowed Mutations

- Allowed: Visible application/viewer theme preferences, panel/viewer state, Wipe & logout, and reauthentication.
- Not allowed: Server/data changes or hidden-state injection.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_34 | Cleared local state through the visible UI, tested default dark in both app themes across activity/map/Statistics, selected light, reloaded and reopened every entry point, measured themed surfaces/focus, switched back to dark, and repeated Details/Nearby/maximize/navigation/zoom. | Every viewer defaults dark without a preference; light changes only viewers and persists across entry points/reload; all named surfaces/focus states adapt; dark restores and keeps every interaction working, including mobile Details and native fullscreen. | All desktop theme/default/persistence/surface/focus and interaction paths passed. Viewer light/dark remained independent from both app themes; computed contrast surfaces changed coherently, focus stayed visible, and dark maximize/Details/Nearby/Previous/Next/zoom passed. Native fullscreen is unavailable in the connected browser and the fixed desktop viewport cannot render the required mobile Details sheet, so those two branches remain blocked. Cleanup removed temporary preferences and restored authenticated light-app/no-viewer-preference state. | BLOCKED | [assets/MED_34-viewer-themes.txt](../assets/MED_34-viewer-themes.txt) |

## Issues

- None. The unavailable native-fullscreen and mobile-render branches are environment constraints, not product findings.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_34-viewer-themes.txt](../assets/MED_34-viewer-themes.txt) | Entry-point defaults, persistence, exact computed surface/focus colors, interaction results, constraints, and cleanup. |

## Screenshot Evidence

Live screenshots were inspected for light and dark desktop layouts. ACC_04 prevents saving them as durable local files, so exact computed styles and DOM states are the durable evidence.

## Timings

| Step | Timing |
|---|---:|
| Viewer theme transitions | Under 1 s each |
| Entry-point opens after loaded parent view | Under 2 s each |

## Handoff Notes

- Completed: Every executable desktop default/persistence/theme/focus/interaction path in both app themes and exact browser-state cleanup.
- Remaining unfinished coverage: None; MED_34 is terminal BLOCKED.
- Blocked or not applicable: Native browser fullscreen and phone Details-sheet rendering are unavailable in the connected browser.
- State left for the next packet: Authenticated light-app root with 8 Tracks and no stored viewer preference; data baseline unchanged.
