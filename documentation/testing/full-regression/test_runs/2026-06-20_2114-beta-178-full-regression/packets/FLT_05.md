# Packet: FLT_05

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: FLT_05
- In scope: Geo drawing for circle, rectangle, polygon; undo, cancel, finish, clear; and saved shapes reappearing next time.
- Out of scope: Exact geospatial inclusion math for drawn areas.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_04
- Required app/data state: Filter panel available with Base scope geo controls.
- Required browser context: Authenticated desktop browser context.

## Allowed Mutations

- Allowed: Draw, cancel, finish, clear, reload, and persist browser filter geo shapes.
- Not allowed: Change imported track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FLT_05 | Attempted the geo drawing control matrix and used FLT_04's direct reload evidence for the saved-shape requirement. | Circle, rectangle, and polygon drawing work; undo, cancel, finish, and clear all work; saved shapes reappear next time. | Original failure: FLT_04 showed a drawn Circle summary before reload, but persisted storage had no `geoCircles` and the Circle was gone after reload. FIXED locally through the same geo-shape persistence change as FLT_04. Additional original toolbar automation attempts stayed inconclusive and were not promoted to a separate issue. | FIXED | [assets/FLT_05-geo-drawing-controls.txt](../assets/FLT_05-geo-drawing-controls.txt); [assets/FLT_04-params-before-reload.webp](../assets/FLT_04-params-before-reload.webp); [assets/FLT_04-params-after-reload.webp](../assets/FLT_04-params-after-reload.webp); [assets/FIXED-filter-planner-local-verification.txt](../assets/FIXED-filter-planner-local-verification.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| FLT-04-P2 | P2 | Geo circle parameter is not persisted or re-applied after reload. | See FLT_04. | Saved geo shapes reappear next time. | FIXED locally: geo draw/clear now persists the current filter draft, giving saved shapes a persisted source to reappear in the next filter session. | [assets/FLT_04-params-before-reload.webp](../assets/FLT_04-params-before-reload.webp); [assets/FLT_04-params-after-reload.webp](../assets/FLT_04-params-after-reload.webp); [assets/FIXED-filter-planner-local-verification.txt](../assets/FIXED-filter-planner-local-verification.txt) | FIXED locally; full browser regression was not rerun after the code change. |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_05-geo-drawing-controls.txt](../assets/FLT_05-geo-drawing-controls.txt) | FLT_05 attempt summary and issue linkage. |
| [assets/FLT_04-params-before-reload.webp](../assets/FLT_04-params-before-reload.webp) | Drawn Circle visible before reload. |
| [assets/FLT_04-params-after-reload.webp](../assets/FLT_04-params-after-reload.webp) | Circle missing after reload while date/text remain. |
| [assets/FIXED-filter-planner-local-verification.txt](../assets/FIXED-filter-planner-local-verification.txt) | Local implementation and focused test evidence for the shared geo persistence fix. |

## Screenshot Evidence

![Circle before reload](../assets/FLT_04-params-before-reload.webp)

![Circle missing after reload](../assets/FLT_04-params-after-reload.webp)

## Timings

| Step | Timing |
|---|---:|
| Geo drawing control attempts and persistence comparison | < 10 min |

## Handoff Notes

- Completed: FLT_05 is terminal `FIXED` locally because saved geo shapes now have a persisted client-config source.
- Remaining unfinished coverage: FLT_06 onward.
- Blocked or not applicable: Detailed polygon-control assertions were not promoted to a new issue because the automation did not produce stable direct evidence beyond the reload persistence failure.
- State left for the next packet: Original remote run state could remain active with date/text params and no persisted geo shape. Local code now persists geo shapes for subsequent sessions.
