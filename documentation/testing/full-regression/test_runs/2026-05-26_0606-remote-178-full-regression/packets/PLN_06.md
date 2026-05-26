# Packet: PLN_06

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: PLN_06
- In scope: planner elevation profile rendering and hover-to-map highlight.
- Out of scope: detailed chart value validation.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_02.
- Required app/data state: active route with elevation profile.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: restore temporary planner route and hover chart.
- Not allowed: save or delete persisted plans.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| PLN_06 | Restored a route, inspected the elevation profile, then hovered the profile line. | Elevation profile renders and hovering it highlights the matching map point. | Profile rendered with route elevation values; hovering showed a chart tooltip (`5.79 km`, `412 m`, `-0.1%`) and an orange hover marker on the visible route point. | PASS | `assets/PLN_06-profile-before-hover.webp`, `assets/PLN_06-profile-hover-retry.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/PLN_06-profile-before-hover.webp | Rendered elevation profile before hover. |
| assets/PLN_06-profile-hover.webp | Initial hover attempt. |
| assets/PLN_06-profile-hover-retry.webp | Successful hover tooltip and map marker. |

## Timings

| Step | Timing |
|---|---:|
| Elevation profile hover check | <4m |

## Handoff Notes

- Completed: PLN_06 terminal PASS.
- Remaining unfinished coverage: continue with PLN_07.
- Blocked or not applicable: none.
- State left for the next packet: unsaved planner route is active and ready to save.
