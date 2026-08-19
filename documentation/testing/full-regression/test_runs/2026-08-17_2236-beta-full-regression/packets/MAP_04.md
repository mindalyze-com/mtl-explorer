# Packet: MAP_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MAP_04
- In scope: Deleted-track absence from map sources, selection lists, and popups.
- Out of scope: Server-only stale URLs and API probes.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_03 and DEL_01-DEL_04.
- Required app/data state: Former tracks 100001 and 100003 processed as removed; retained tracks still indexed.
- Required browser context: Signed-in desktop map with current 15-track data.

## Allowed Mutations

- Allowed: Location search, direct map clicks, selector searches, presentation-only Heatmap enable, and retained-detail navigation.
- Not allowed: Additional data deletion.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_04 | Searched deleted targets in the track selector/browser, centered and clicked their former Vitry and Le Thillot map areas at 100 m, enabled the current heatmap, and opened a retained import. | Deleted tracks leave map sources, selection lists, and popups while retained tracks stay usable. | Neither target appeared in selector/browser; direct former-area clicks produced no stale selection, details, name, or popup; current map/heatmap reported 15 tracks; retained 100004 opened normally. | PASS | [assets/MAP_04-deletion-map.txt](../assets/MAP_04-deletion-map.txt); [assets/MAP_04-vitry-absence.webp](../assets/MAP_04-vitry-absence.webp); [assets/DEL_04-retained-related.webp](../assets/DEL_04-retained-related.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_04-deletion-map.txt](../assets/MAP_04-deletion-map.txt) | Exact source, selector, former-area popup, heatmap, and retained-details results. |
| [assets/MAP_04-vitry-absence.webp](../assets/MAP_04-vitry-absence.webp) | Former Vitry start area at 100 m without stale selection or popup. |
| [assets/DEL_04-retained-related.webp](../assets/DEL_04-retained-related.webp) | Retained import still opens with its selected-track map and related records. |

## Screenshot Evidence

![Former Vitry route start without a stale selection or popup](../assets/MAP_04-vitry-absence.webp)

## Timings

| Step | Timing |
|---|---:|
| Location result to 100 m map | Under 0.7 seconds after selection |
| Direct click settle | 0.5 seconds each |
| Heatmap live enable | Under 0.5 seconds |

## Handoff Notes

- Completed: Deleted-source selector, former-route, popup, heatmap, and retained-detail checks.
- Remaining unfinished coverage: None for MAP_04.
- Blocked or not applicable: None.
- State left for the next packet: Current 15-track map; heatmap enabled; no stale deleted-track UI.
