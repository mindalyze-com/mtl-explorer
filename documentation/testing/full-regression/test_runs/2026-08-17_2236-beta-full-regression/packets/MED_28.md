# Packet: MED_28

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_28
- In scope: Server-paged activity timeline/markers, capture-time order, 100-item default, 200-item cap, current-page-only DOM, and first/middle/last large-library navigation.
- Out of scope: Viewer-page buffering, covered by MED_33.

## Prerequisites

- Required previous coverage IDs or run packets: MED_27 and MED_21.
- Required app/data state: Matching synthetic activity and MED_21 100,000-row media dataset.
- Required browser context: Signed-in Track Details Photos tab.

## Allowed Mutations

- Allowed: Page and page-size navigation.
- Not allowed: Direct database fixture seeding.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_28 | Opened activity 100016 Photos, counted and ordered timeline/mini-map items, then probed live page defaults and bounds. | Default 100, maximum 200, current-page-only rendering, responsive first/middle/last pages on 100,000 rows. | Six UI rows and markers rendered in capture-time order; live API defaulted to 100, accepted 200, rejected 201, and returned in 0.09-0.14 s. The frozen run has six rows, not the required 100,000, so multi-page behavior is unreachable. | BLOCKED | [assets/MED_28-paging.txt](../assets/MED_28-paging.txt) |

## Issues

- Missing MED_21 scale fixture, not a failure of the observed six-row path.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_28-paging.txt](../assets/MED_28-paging.txt) | UI counts/order plus live bounded-response timings and limits. |

## Screenshot Evidence

- Live screenshot inspection showed the six circular camera markers on the mini-map; exact semantic counts and labels are in the linked evidence.

## Timings

| Step | Timing |
|---|---:|
| Default live response | 90 ms |
| Explicit 100/200 response | 93 ms / 89 ms |
| Rejected 201 response | 140 ms |

## Handoff Notes

- Completed: Small-dataset ordering, item/marker count, 100 default, 200 cap, and current-page DOM.
- Remaining unfinished coverage: None for MED_28; unavailable large-dataset children are recorded as blocked.
- Blocked or not applicable: 100,000-row first/middle/last page journey.
- State left for the next packet: Track 100016 Photos open with all six rows visible.
