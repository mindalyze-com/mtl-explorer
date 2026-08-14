# Packet: FLT_10

> **FIX FOLLOW-UP — 2026-08-14: FIXED AND VERIFIED.** The original beta failure below is retained as run history. See [follow-up evidence](../fix-verification.md#resolution-matrix).

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: FLT_10.
- In scope: result-category selection for main activity groups and exact activity types.
- Out of scope: editing a track's activity type.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_09.
- Required app/data state: Bicycle 11 and Walking 1 across 12 tracks.
- Required browser context: Filter view catalog, Included categories, map count, and legend.

## Allowed Mutations

- Allowed: select ON_FOOT and switch between activity views.
- Not allowed: change track activity values to manufacture a missing filter capability.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FLT_10 | Selected ON_FOOT under Activities by type, then audited catalog/config and Included categories for exact Walking/Hiking selection. | Main groups and exact activity types can each be selected with correct labels/counts. | Main grouping passed: ON_FOOT 1 produced one track. Exact activity selection is absent: both activity views expose only CYCLING/ON_FOOT, and no catalog/config view exposes Walking or Hiking as result categories. | FAIL | [activity category audit](../assets/FLT_10-activity-categories.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| FLT-10-P2 | P2 | Exact activity types are not available as filter result categories. | Open Filter view catalog and Included categories for both activity views. | Select Walking, Hiking, and other exact activity types independently. | Only CYCLING and ON_FOOT main groups are available. | [activity category audit](../assets/FLT_10-activity-categories.txt) | Users cannot distinguish Walking from Hiking/Running or Bicycle from Mountain biking in result-category selection. |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_10-activity-categories.txt](../assets/FLT_10-activity-categories.txt) | Working main-group selection and missing exact-type UI/config audit. |

## Screenshot Evidence

Exact category labels/counts and the complete view/config inventory are recorded in text.

## Timings

| Step | Timing |
|---|---:|
| Main group selection | < 1 s |
| Activity view/config audit | < 2 min |

## Handoff Notes

- Completed: FLT_10 is terminal `FAIL` with FLT-10-P2.
- Remaining unfinished coverage: FLT_11 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: Activities by keyword, no criteria, all two main categories, all 12 tracks.
