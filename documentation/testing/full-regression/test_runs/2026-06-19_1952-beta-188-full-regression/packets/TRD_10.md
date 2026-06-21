# Packet: TRD_10

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: TRD_10
- In scope: Change activity type and verify save plus automatic energy/calorie recalculation.
- Out of scope: Temporary what-if rider weight, covered by TRD_11.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_09.
- Required app/data state: Track #100005 openable and included in statistics.
- Required browser context: authenticated desktop detail page.

## Allowed Mutations

- Allowed: Change activity type and restore visible type afterward.
- Not allowed: Leave a different visible activity type or changed energy totals.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_10 | Opened Track #100005, changed visible activity type from Walking to Bicycle, read API energy fields, then changed visible type back to Walking and re-read API. | Activity type saves successfully and energy/calorie values update automatically. | UI saved Bicycle; API changed to `BICYCLE/USER_SET` and energy changed from 346.67 Wh to 395.1 Wh. Restoring Walking returned energy to 346.67 Wh. | PASS | [assets/TRD_10-activity-type-energy.txt](../assets/TRD_10-activity-type-energy.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_10-activity-type-energy.txt](../assets/TRD_10-activity-type-energy.txt) | UI action and API before/change/final activity and energy values. |

## Screenshot Evidence

No screenshot asset was captured for this packet; direct UI state and API value evidence is recorded in the text asset.

## Timings

| Step | Timing |
|---|---:|
| Activity change, verify, restore | ~3 min |

## Handoff Notes

- Completed: TRD_10.
- Remaining unfinished coverage: TRD_11 onward.
- Blocked or not applicable: none.
- State left for the next packet: Track #100005 visible activity type and energy are restored to Walking/346.67 Wh; `activityTypeSource` remains `USER_SET` because no reset-to-auto control exists in the exposed API/UI.
