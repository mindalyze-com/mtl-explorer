# Packet: MED_28

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_28
- In scope: Server-paged activity timeline rows and mini-map markers, capture-time order, default/maximum page size, first/middle/last pages, responsiveness, and bounded DOM on 100,000 media rows.
- Out of scope: Global viewer paging and ID selection, covered by MED_27 and MED_33.

## Prerequisites

- Required previous coverage IDs or run packets: MED_27 and the MED_21 large-library fixture design.
- Required app/data state: Normal application startup completed before the corrected 100,000-row/300-track synthetic seed.
- Required browser context: Authenticated desktop Track Details page for synthetic track 2000000.

## Allowed Mutations

- Allowed: Corrected MED_21-scale direct database seed, authenticated bounded HTTP reads, UI page changes, and exact bounded cleanup.
- Not allowed: Leaving any synthetic performance row or changing an original media row.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_28 | Seeded bounded activity media and exercised default and 200-item visible pages; retained the original 100,000-row backend/DOM evidence. | Photos defaults to 100 items, accepts at most 200, renders only current-page rows/markers in capture-time order, and stays responsive. | Fixed locally: a 211-item activity showed 1-100 by default and 1-200 after selecting 200; mobile reload restored default 100 and options 100/200. Original backend cap/order/bounded-DOM checks remain valid. | FIXED | [original](../assets/MED_28-paging.txt); [local retest](../assets/MTL-FR-005-021-fix-local.txt); [desktop](../assets/MTL-FR-014-016-fix-local-desktop.webp); [mobile](../assets/MTL-FR-014-016-fix-local-mobile.webp) |

## Issues

- MTL-FR-014 (P2, FIXED locally): Activity Photos now defaults to 100 and exposes 100/200; direct desktop/mobile paging and the existing backend bounds pass.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_28-paging.txt](../assets/MED_28-paging.txt) | Exact UI pages, row/marker counts, order, timings, backend bounds, fixture handling, and cleanup. |
| [assets/MED_28-seed.sql](../assets/MED_28-seed.sql) | Corrected live-enum 100,000-media/300-track seed. |
| [assets/MED_28-cleanup.sql](../assets/MED_28-cleanup.sql) | Bounded removal of every synthetic MED_28 row. |

## Screenshot Evidence

![Desktop 100-item activity media page](../assets/MTL-FR-014-016-fix-local-desktop.webp)

![Mobile 100-item activity media page](../assets/MTL-FR-014-016-fix-local-mobile.webp)

## Fix Record

- Shared track-media paging constants set default 100 and options 100/200 for the list and viewer buffer.
- Full client suite 757/757 and direct desktop/mobile page-size checks pass.
- See [local evidence](../assets/MTL-FR-005-021-fix-local.txt).

## Timings

| Step | Timing |
|---|---:|
| Initial Photos transition | 1.283 s including settle |
| Middle-page transitions | 0.699-0.715 s each including settle |
| Last-page transition | 0.777 s including settle |
| Backend 100 / 200 / rejected 201 | 50.082 / 53.647 / 12.568 ms |

## Handoff Notes

- Completed: Large seed, UI/API page bounds, first/middle/last pages, row-marker parity, capture-time order, responsiveness, finding capture, and exact cleanup.
- Remaining unfinished coverage: None for MED_28.
- Blocked or not applicable: Durable screenshots remain blocked by ACC_04.
- State left for the next packet: Root map with 8 Tracks; original media/resolved/selected counts 8/8/8; no synthetic MED_28 rows; both correlation queues empty.
