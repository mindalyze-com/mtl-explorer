# Packet: PLN_07

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: PLN_07
- In scope: save plan, list saved plans, load saved plan, delete saved plan.
- Out of scope: GPX export, covered by PLN_08.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_02.
- Required app/data state: active unsaved planner route.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: create and delete one disposable saved route.
- Not allowed: leave saved test routes behind.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| PLN_07 | Saved `Regression PLN07 disposable route`, opened Load, loaded it, then deleted it from the saved route list. | Save plan, list saved plans, load a saved plan, and delete a plan all work. | Save dialog accepted name/description; Load showed the saved route with 5.8 km metadata; selecting it restored the Drawing view with the route; delete confirmation removed it and the list returned to `No saved routes yet`. | PASS | `assets/PLN_07-save-dialog.webp`, `assets/PLN_07-load-list.webp`, `assets/PLN_07-loaded-plan.webp`, `assets/PLN_07-delete-dialog.webp`, `assets/PLN_07-after-delete.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/PLN_07-save-dialog.webp | Save route dialog with disposable plan metadata. |
| assets/PLN_07-after-save.webp | Dialog closed after save. |
| assets/PLN_07-load-list.webp | Saved route visible in Load list. |
| assets/PLN_07-loaded-plan.webp | Saved route loaded back into Drawing. |
| assets/PLN_07-delete-dialog.webp | Delete saved-route confirmation. |
| assets/PLN_07-after-delete.webp | Saved-route list empty after deletion. |

## Timings

| Step | Timing |
|---|---:|
| Save/list/load/delete route | <8m |

## Handoff Notes

- Completed: PLN_07 terminal PASS.
- Remaining unfinished coverage: continue with PLN_08.
- Blocked or not applicable: none.
- State left for the next packet: saved-route list empty; the loaded route remains available in Drawing state.
