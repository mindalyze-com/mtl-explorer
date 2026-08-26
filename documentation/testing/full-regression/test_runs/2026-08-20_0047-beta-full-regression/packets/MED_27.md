# Packet: MED_27

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_27
- In scope: Cluster, map-view, activity, single-photo, paging, camera-preservation, and desktop/390 x 760 media map navigation.
- Out of scope: Activity timeline paging, covered by MED_28.

## Prerequisites

- Required previous coverage IDs or run packets: MED_26.
- Required app/data state: Eight indexed regression media items with resolved Bern positions and matching activity correlations.
- Required browser context: Signed-in main map; desktop context available, required 390 x 760 context unavailable.

## Allowed Mutations

- Allowed: Map pan/zoom, temporary map-layer visibility, and temporary category filtering.
- Not allowed: Changing media rows or replacing the frozen fixture.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_27 | Exercised the eight-photo cluster, current-map collection, one-activity direct destination, four-activity chooser, and separated single-photo marker on desktop; attempted to prepare the required phone and adjacent-page branches. | Every chooser destination and scope works without changing the camera; cluster navigation stays bounded and lazily crosses adjacent pages; the full flow repeats at 390 x 760. | Desktop cluster/map/activity/single-photo paths passed with exact scope labels and unchanged 100 m or 20 m camera scale. The fixed 1049 x 942 browser cannot supply the required 390 x 760 context, and the frozen eight-item fixture cannot cross the 200-member page boundary. | BLOCKED | [assets/MED_27-browser-flow.txt](../assets/MED_27-browser-flow.txt) |

## Issues

None. The unavailable phone context and insufficient cluster cardinality are recorded as coverage blockers, not product findings.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_27-browser-flow.txt](../assets/MED_27-browser-flow.txt) | Exact chooser labels, destinations, viewer scopes, camera scales, blockers, and cleanup state. |

## Screenshot Evidence

Live screenshot inspection verified the rendered cluster and separated markers. ACC_04 prevents saving the in-app screenshot result as a durable local asset, so the linked text capture is the durable evidence.

## Timings

| Step | Timing |
|---|---:|
| Chooser/viewer transitions | Under 1 s each |
| Three-step marker-separation zoom | About 2 s including settle |

## Handoff Notes

- Completed: All desktop chooser destinations, both collection scopes, direct and multi-activity routing, camera preservation, bounded eight-item navigation, and the single-photo branch.
- Remaining unfinished coverage: None for MED_27; blocked children are terminally recorded above.
- Blocked or not applicable: Full 390 x 760 repeat and adjacent cluster-page lazy loading.
- State left for the next packet: Filter reset to all 8 tracks; GPS tracks, media, and trackpoints shown; heatmap hidden; no viewer or navigation sheet open.
