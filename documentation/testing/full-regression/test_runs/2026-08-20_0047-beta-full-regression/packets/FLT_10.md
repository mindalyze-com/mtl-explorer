# Packet: FLT_10

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FLT_10
- In scope: Main activity-group and exact activity-type category selection, labels, and counts.
- Out of scope: Selection persistence while criteria change, covered by FLT_11.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_09.
- Required app/data state: Nine active tracks with seven Bicycle and two Walking activities.
- Required browser context: Authenticated Filter and map views.

## Allowed Mutations

- Allowed: Switch filter views and select activity categories.
- Not allowed: Edit track activity values.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FLT_10 | Select ON_FOOT in main groups, then WALKING in exact types. | Main-group and exact-type labels and counts remain distinct and correct. | ON_FOOT 2 and WALKING 2 each produced the correct two-track result; CYCLING/BICYCLE each showed 7. HIKING was absent because the run has no Hiking track. | PASS | [assets/FLT_10-activity-categories.txt](../assets/FLT_10-activity-categories.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_10-activity-categories.txt](../assets/FLT_10-activity-categories.txt) | Exact broad-group and exact-type labels, counts, and result transitions. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible labels and counts are linked above.

## Timings

| Step | Timing |
|---|---:|
| Main-group and exact-type selection | 4 min |

## Handoff Notes

- Completed: Main and exact activity-category selection.
- Remaining unfinished coverage: None for FLT_10.
- Blocked or not applicable: HIKING was unavailable in the dataset; the exact-type behavior was exercised with WALKING.
- State left for the next packet: Activities by exact type with only WALKING selected; two-track result.
