# Packet: FLT_12

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FLT_12
- In scope: Intentional/stable select-none result and select-everything normalization to All categories.
- Out of scope: Unavailable selected category behavior covered by FLT_13.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_11.
- Required app/data state: Exact type view with three available categories and no criteria.
- Required browser context: Included categories selector and Filter result.

## Allowed Mutations

- Allowed: Clear category selection, reload, then select every current category.
- Not allowed: Leave an empty selection after this packet.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FLT_12 | Applied no categories, reloaded, reopened selector, then used Select current and applied all categories. | Empty is intentional/stable; selecting every available category normalizes to All categories when no unavailable selection exists. | Empty remained 0/15 and all boxes unchecked after reload. Select current checked all three plus All categories, and Apply normalized to All 3 categories / 15 tracks. | PASS | [assets/FLT_12-none-all.txt](../assets/FLT_12-none-all.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_12-none-all.txt](../assets/FLT_12-none-all.txt) | Empty, reloaded, all-selected, and normalized summary/control states. |

## Screenshot Evidence

Unavailable under ACC_04. Exact checkbox states, summaries, counts, toolbar values, and reload state provide direct DOM evidence.

## Timings

| Step | Timing |
|---|---:|
| Select none and apply | About 1 s |
| Reload stability check | About 2 s |
| Select all and apply | About 1 s |

## Handoff Notes

- Completed: Stable empty result and All categories normalization.
- Remaining unfinished coverage: None for FLT_12.
- Blocked or not applicable: None.
- State left for the next packet: Filter open; exact activity view normalized to All 3 categories; track 100017 remains temporarily Hiking.
