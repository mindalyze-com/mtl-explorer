# Packet: ADM_09

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: ADM_09
- In scope: Public/signed-in About, expected credits, and return behavior.

## Prerequisites

- Required previous coverage IDs or run packets: ADM_01 signed-in overlay navigation and ADM_08 Admin origin.
- Required app/data state: Signed-in primary origin plus a fresh signed-out hostname/origin.
- Required browser context: Desktop browser.

## Allowed Mutations

- Allowed: Open/close About overlays/routes; create and close one temporary signed-out tab.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ADM_09 | Opened About before login and after login, expanded credits, and exercised overlay/direct close paths. | Expected map/library/data sources appear; Back returns to Admin or login/map fallback. | Public and signed-in content matched; 12 expected source credits were explicit; close restored Admin Preferences, signed-out login, or signed-in map fallback as appropriate. | PASS | [assets/ADM_09-about-credits.txt](../assets/ADM_09-about-credits.txt) |

## Issues

No new issue. The already-recorded imprecise `Version dev` build identity remains MTL-FR-001/FIXED by local verification; the deployed image is unchanged.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/ADM_09-about-credits.txt](../assets/ADM_09-about-credits.txt) | Signed-out/signed-in content, sources, and return matrix. |

## Screenshot Evidence

Live desktop inspection confirmed all About states. ACC_04 prevents durable screenshot saving.

## Timings

| Step | Timing |
|---|---:|
| Each About open/close | Under 0.7 s |

## Handoff Notes

- Completed: Public and authenticated About, source credits, and all required return paths.
- Remaining unfinished coverage: None for ADM_09.
- Blocked or not applicable: Durable screenshots only.
- State left for the next packet: Primary signed-in tab on the 8-track map; temporary signed-out tab closed.
