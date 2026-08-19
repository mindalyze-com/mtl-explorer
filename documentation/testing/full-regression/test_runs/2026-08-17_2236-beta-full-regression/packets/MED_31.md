# Packet: MED_31

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_31
- In scope: 390 x 760 and 375 x 667 photo-first viewer, mobile Details sheet reachability, compact Nearby state/label, and safe-area/padding edges.
- Out of scope: Desktop viewer interactions, covered by MED_30.

## Prerequisites

- Required previous coverage IDs or run packets: MED_30.
- Required app/data state: Six-photo activity viewer.
- Required browser context: Fresh live phone-sized viewports.

## Allowed Mutations

- Allowed: Viewer-only disclosure/scroll and temporary viewport overrides.
- Not allowed: Persisted media changes.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_31 | Inspected the Nearby dock on the fixed build at desktop, 390 x 844, 375 x 667, and 390 x 760. | Desktop uses Nearby photos; compact layouts use Nearby while retaining the full accessible name. | The visible label was Nearby photos on desktop and Nearby at every phone size in expanded and collapsed states. | FIXED | [details](../assets/MED_31-remediation.txt); [desktop](../assets/MED_31-fixed-desktop.webp); [mobile](../assets/MED_31-fixed-mobile.webp); [375x667](../assets/MED_31-fixed-375x667.webp); [390x760](../assets/MED_31-fixed-390x760.webp) |

## Issues

| Issue | Severity | Summary | Reproduction | Expected | Actual | Evidence | Impact |
|---|---|---|---|---|---|---|---|
| FR-012 | P2 | Phone viewer does not shorten the Nearby dock label. | At 375 x 667 or 390 x 760, open activity 100016 Photos, open a photo, then inspect expanded and collapsed dock labels. | Visible phone label is "Nearby". | Visible label remains "Nearby photos" in both states and both viewports. | [assets/MED_31-mobile-viewer.txt](../assets/MED_31-mobile-viewer.txt) | Wastes limited phone dock width and violates the explicit responsive copy contract. |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_31-mobile-viewer.txt](../assets/MED_31-mobile-viewer.txt) | Per-viewport layout/action results and exact label failure. |

## Screenshot Evidence

- Live 375 x 667 and 390 x 760 screenshots showed photo-first open, the bottom-reaching dark Details sheet, and expanded/collapsed dock states with the failing visible label.

## Timings

| Step | Timing |
|---|---:|
| Viewer open | Under 500 ms |
| Details open and scroll | Under 1 s |
| Viewport reflow | Under 400 ms |

## Handoff Notes

- Completed: Full MED_31 at both required live viewport sizes.
- Remaining unfinished coverage: None for MED_31.
- Blocked or not applicable: None.
- State left for the next packet: Temporary phone tab closed; viewport reset to 1280 x 720; primary tab remains on root map.

## Remediation Verification

- Finding FR-012 is `FIXED`: responsive labels now match the required desktop and phone copy.
- The button's full accessible name and custom collection labels are unchanged.
