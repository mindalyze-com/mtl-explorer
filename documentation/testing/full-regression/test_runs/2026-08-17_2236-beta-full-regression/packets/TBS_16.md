# Packet: TBS_16

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TBS_16
- In scope: Dated/undated media mosaics, filters/order/paging/states, shared viewer navigation, and track-related activity return.
- Out of scope: Track Photos tools beyond confirming the destination tab.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_15.
- Required app/data state: Six prescribed dated photos linked to activity 100016; no undated or 61-plus-item fixture.
- Required browser context: Statistics Trends Media chart, shared viewer, and Track Details Photos.

## Allowed Mutations

- Allowed: Switch media mode/filter, open/close mosaic/viewer, navigate within the viewer, and open the linked activity.
- Not allowed: Add bulk or undated media outside the prescribed fixture set.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TBS_16 | Opened the populated 2026-08 stack, cycled mosaic filters, opened/navigated/closed the viewer, then repeated in Matched only and opened the activity. Audited missing Undated/paging/error preconditions. | Correct total/filters/order, 60-item paging, recoverable states, shared viewer, undated drill-down, and mode-correct Open activity. | Six-item period, All/Photos/Videos, ordering, viewer 1/6→2/6, all-indexed action absence, and Matched-only return to track 100016 Photos all passed. No undated item, 61-plus-item page, or deterministic error fixture exists, blocking those children. | BLOCKED | [assets/TBS_16-media-mosaic.txt](../assets/TBS_16-media-mosaic.txt); [assets/DAT_08-media-manifest.json](../assets/DAT_08-media-manifest.json) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_16-media-mosaic.txt](../assets/TBS_16-media-mosaic.txt) | Mosaic, viewer, activity-return results, and exact blocked children. |
| [assets/DAT_08-media-manifest.json](../assets/DAT_08-media-manifest.json) | Six-item dated fixture boundary. |

## Screenshot Evidence

Unavailable under ACC_04. Mosaic totals/order, viewer position/filmstrip, route/tab identity, and manifest provide direct evidence.

## Timings

| Step | Timing |
|---|---:|
| Period mosaic and filters | About 5 s |
| Shared viewer navigation | About 4 s |
| Matched-only activity return | About 5 s |

## Handoff Notes

- Completed: Populated period, filters/order, viewer, action absence, and linked-activity return.
- Remaining unfinished coverage: None for TBS_16; the packet is terminal BLOCKED for missing Undated, paging-boundary, and error fixtures.
- Blocked or not applicable: Undated drill-down, 60-item Load more, and recoverable loading/error state.
- State left for the next packet: Track 100016 Details is open on Photos; no active filter; stable 13-track set.
