# Packet: IMP_07

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: IMP_07
- In scope: Zoom to each imported map geometry, click a known track point, verify its selection popup/detail, visible line response, and absence of stale or duplicated choices.
- Out of scope: Statistics totals, covered by IMP_08 and IMP_09.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_06.
- Required app/data state: Five imported tracks visible in the current filter result.
- Required browser context: Signed-in desktop map.

## Allowed Mutations

- Allowed: Use location search, zoom, line clicks, selection popups, and detail sheets.
- Not allowed: Change filters, source files, or imported records.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| IMP_07 | Center and zoom the main map near each known source GPX point; click each imported line and open the resulting selection/detail. | All five lines respond with the expected track, popup/detail, and geometry; no stale or duplicated line choices. | All five tracks were selected from line clicks. Three opened directly. The one overlapping point showed exactly the two legitimate Mosel/Voie choices, each once, and both opened correctly. | PASS | [assets/IMP_07-map-interaction.txt](../assets/IMP_07-map-interaction.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/IMP_07-map-interaction.txt](../assets/IMP_07-map-interaction.txt) | Per-track location, zoom scale, line-click result, popup behavior, and detail identity. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; visible map interaction results are recorded in the linked asset.

## Timings

| Step | Timing |
|---|---:|
| Five map searches, zooms, line selections, and detail checks | 18 min |

## Handoff Notes

- Completed: Main-map line interaction, selection popup/detail opening, and duplicate-choice check for all five imports.
- Remaining unfinished coverage: None for IMP_07.
- Blocked or not applicable: None.
- State left for the next packet: Track 100000 details are open; imported data is unchanged.
