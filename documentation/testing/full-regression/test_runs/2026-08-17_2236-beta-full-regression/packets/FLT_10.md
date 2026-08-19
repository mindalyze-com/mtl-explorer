# Packet: FLT_10

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FLT_10
- In scope: Exact result selection for main activity groups and exact Walking/Hiking activity types.
- Out of scope: Cross-parameter selection persistence covered by FLT_11.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_09 and TRD_10.
- Required app/data state: Two Walking tracks plus controlled track 100017 temporarily changed to Hiking.
- Required browser context: Filter category selectors and Review tracks.

## Allowed Mutations

- Allowed: Temporarily change track 100017 Running→Hiking through the UI and retain it through FLT_15.
- Not allowed: Forget to restore Running after FLT_15.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FLT_10 | Selected only ON_FOOT in main groups, then selected HIKING and WALKING in exact types and inspected Review tracks. | Main/exact labels and counts remain correct. | ON_FOOT counted 3. Exact BICYCLE/HIKING/WALKING counted 12/1/2; selecting HIKING+WALKING produced three matching rows with the correct activities. | PASS | [assets/FLT_10-activity-categories.txt](../assets/FLT_10-activity-categories.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_10-activity-categories.txt](../assets/FLT_10-activity-categories.txt) | Controlled Hiking provenance plus main/exact selections, counts, legend, and result rows. |

## Screenshot Evidence

Unavailable under ACC_04. Exact rendered labels, counts, selected categories, and row identities provide direct DOM evidence.

## Timings

| Step | Timing |
|---|---:|
| Controlled activity change | About 2 s |
| Main-group selection | About 3 s |
| Exact-type selection and review | About 4 s |

## Handoff Notes

- Completed: Main activity group and exact Walking/Hiking selection.
- Remaining unfinished coverage: None for FLT_10.
- Blocked or not applicable: None.
- State left for the next packet: Filter Review open; Activities by exact type with HIKING+WALKING selected; track 100017 remains temporarily Hiking for FLT_11-FLT_15.
