# Packet: MED_33

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_33
- In scope: A true centered 100,000-item activity viewer and a paged 99,999-photo map cluster, including bounded DOM/list windows, direct selection, bidirectional page boundaries, requests, and timings.
- Out of scope: General media page-size controls already isolated by MED_28.

## Prerequisites

- Required previous coverage IDs or run packets: MED_32 terminal result.
- Required app/data state: Eight-item baseline and disposable capacity for a bounded 99,992-row fixture.
- Required browser context: Authenticated desktop activity and main-map viewers.

## Allowed Mutations

- Allowed: Exact disposable MED_33 database rows and ephemeral browser viewer/map state.
- Not allowed: Persistent fixture rows, original media changes, or unbounded DOM assertions based only on screenshots.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_33 | Seeded an exact 100,000-item activity, used 1,000 visible page transitions to select item 50,000, exercised direct and bidirectional activity boundaries, repeated paging/direct/bidirectional boundaries in the 99,999-photo map cluster, recorded list/DOM counts and timings, then removed the fixture exactly. | Both viewer sources use bounded 200-ID pages/windows, keep the centered current item/count correct, shift both ways at boundaries, and keep the thumbnail DOM bounded without an enormous array. | Original bounded map/activity behavior passed except the 50-item activity page. The fixed client now exposes and supplies a bounded 200-item activity page; desktop selected 200 and rendered 200 rows, while mobile exposed the same 100/200 contract. | FIXED | [original](../assets/MED_33-large-viewer.txt); [local retest](../assets/MTL-FR-005-021-fix-local.txt); [desktop](../assets/MTL-FR-014-016-fix-local-desktop.webp); [mobile](../assets/MTL-FR-014-016-fix-local-mobile.webp) |

## Issues

- MTL-FR-014 (P2, FIXED locally): the activity viewer can now receive the selected 200-item page/window while keeping the original bounded DOM behavior.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_33-large-viewer.txt](../assets/MED_33-large-viewer.txt) | Exact activity/map counts, boundaries, requests, timings, DOM bounds, and cleanup results. |
| [assets/MED_33-seed.sql](../assets/MED_33-seed.sql) | Reproducible 99,992-row fixture making the activity total exactly 100,000. |
| [assets/MED_33-cleanup.sql](../assets/MED_33-cleanup.sql) | Exact fixture removal. |

## Screenshot Evidence

![Desktop bounded activity media window](../assets/MTL-FR-014-016-fix-local-desktop.webp)

![Mobile bounded activity media window](../assets/MTL-FR-014-016-fix-local-mobile.webp)

## Fix Record

- Shared track-media paging now supplies default 100 and selectable 200 to the activity viewer buffer.
- The existing exact 100,000-item bounded-DOM and navigation measurements remain the performance evidence.
- See [local evidence](../assets/MTL-FR-005-021-fix-local.txt).

## Timings

| Step | Timing |
|---|---:|
| 1,000 visible activity page transitions | 406,221 ms total |
| Center item open | 890 ms |
| Activity forward/back boundaries | 1,008 / 1,052 ms |
| Map chooser / cluster viewer open | 1,487 / 1,022 ms |
| Map forward / forward / backward boundary batches | 1,631 / 1,582 / 2,517 ms |

## Handoff Notes

- Completed: Exact centered activity test, paged map-cluster repeat, direct/bidirectional boundaries, DOM/list/request/timing capture, and exact cleanup.
- Remaining unfinished coverage: None for MED_33.
- Blocked or not applicable: Durable screenshot saving remains blocked by ACC_04; performance evidence is textual and measured.
- State left for the next packet: Root map with 8 Tracks; 8/8/8 media baseline; empty work queues; no MED_33 rows.
