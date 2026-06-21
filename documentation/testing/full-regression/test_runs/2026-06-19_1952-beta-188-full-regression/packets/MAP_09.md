# Packet: MAP_09

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MAP_09
- In scope: Verify clicking an area where several tracks overlap opens a selection list and choosing one opens details.
- Out of scope: Single-track click, covered by MAP_08.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_08 and DAT_07.
- Required app/data state: Current dataset plus synthetic overlap tracks imported for this packet.
- Required browser context: desktop map tab.

## Allowed Mutations

- Allowed: Import staged fully synthetic shared-zone tracks to create a valid overlap condition.
- Not allowed: Use private GPX data or alter existing real/public imports.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_09 | Imported staged synthetic overlap data plus one synthetic crossing track, confirmed two visible unique tracks overlap by proximity API, opened one from Stats to center the map, then clicked the map center/grid. | Clicking the overlapping map area opens a selection list; selecting one item opens its details. | Overlap data exists: proximity API returned `[100021, 100023]` within 20 m and the UI showed 13 rendered tracks. However, center/grid canvas clicks did not open a selection list or details, so the user-facing selection-list assertion could not be completed with available browser hit-testing. | BLOCKED | [assets/MAP_09-overlap-selection-blocked.txt](../assets/MAP_09-overlap-selection-blocked.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_09-overlap-selection-blocked.txt](../assets/MAP_09-overlap-selection-blocked.txt) | Synthetic overlap import, backend overlap proof, UI count, and blocked map-click attempts. |

## Screenshot Evidence

No screenshot asset was captured for this packet; blocked state is based on DOM/API evidence plus failed canvas click attempts.

## Timings

| Step | Timing |
|---|---:|
| Synthetic overlap import and index wait | ~4 min |
| UI centering/click attempts | ~4 min |

## Handoff Notes

- Completed: MAP_09 as terminal BLOCKED.
- Remaining unfinished coverage: MAP_10 onward.
- Blocked or not applicable: Selection list opening remains blocked by deterministic canvas hit-testing limits. Unblock with manual visual browser testing or instrumented map feature-hit targeting.
- State left for the next packet: Dataset now has 14 API tracks and 13 visible simplified/map tracks; synthetic overlap files remain in the watched import folder.
