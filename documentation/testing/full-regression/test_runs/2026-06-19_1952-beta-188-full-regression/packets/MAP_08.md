# Packet: MAP_08

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MAP_08
- In scope: Verify clicking a single rendered track opens/highlights its details.
- Out of scope: Multi-track overlap selection list, covered by MAP_09; track-point popup metrics, covered by MAP_11.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_07.
- Required app/data state: Imported GPX tracks loaded and visible.
- Required browser context: desktop map/detail context from IMP_07.

## Allowed Mutations

- Allowed: Reuse direct map-click evidence from completed IMP_07.
- Not allowed: Change data or server state.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_08 | Reused the completed IMP_07 action: closed details after centering on Jura #100002, then clicked a visible rendered Jura line segment. | Clicking a single track highlights/opens the corresponding details. | The visible rendered line click reopened Track Details #100002, confirming the single-track map hit path opened the details surface. | PASS | [assets/IMP_07-map-click-results.txt](../assets/IMP_07-map-click-results.txt); [assets/IMP_07-track-100002-after-line-click.webp](../assets/IMP_07-track-100002-after-line-click.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/IMP_07-map-click-results.txt](../assets/IMP_07-map-click-results.txt) | Line-click action and result summary. |
| [assets/IMP_07-track-100002-after-line-click.webp](../assets/IMP_07-track-100002-after-line-click.webp) | Track Details #100002 after rendered-line click. |

## Screenshot Evidence

![Jura detail after rendered-line click](../assets/IMP_07-track-100002-after-line-click.webp)

## Timings

| Step | Timing |
|---|---:|
| Single-track click verification | Covered in IMP_07 |

## Handoff Notes

- Completed: MAP_08.
- Remaining unfinished coverage: MAP_09 onward.
- Blocked or not applicable: none for this single-track click case.
- State left for the next packet: Current browser tab may still be in Map settings from MAP_07; use fresh/root tab if needed.
