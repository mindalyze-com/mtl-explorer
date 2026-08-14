# Packet: FLT_15

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: FLT_15.
- In scope: exact result-category selection restoration across reload and filter-aware views.
- Out of scope: temporary map-legend visibility, covered next.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_14.
- Required app/data state: Tracks by quarter with exact Q1 selection and eight matching tracks.
- Required browser context: Filter, map, Statistics, and shared track browser.

## Allowed Mutations

- Allowed: reload, navigate among filter-aware views, and open Review tracks.
- Not allowed: change the persisted filter during verification.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FLT_15 | Reloaded with exact Q1 selected, sampled the resolving state, then checked map, Filter, Statistics, and Review tracks. | Exact selection is restored before first resolution and every filter-aware view matches. | The restored quarter view went from Updating directly to eight tracks without an unfiltered result. Map, Filter, Statistics, and Review tracks all reported eight of twelve. | PASS | [reload state](../assets/FLT_15-reload-restoration.txt), [review](../assets/FLT_15-restored-review.webp) |

## Issues

No issue found.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_15-reload-restoration.txt](../assets/FLT_15-reload-restoration.txt) | Timed early/settled state and cross-view result counts. |
| [assets/FLT_15-restored-review.webp](../assets/FLT_15-restored-review.webp) | Restored exact-result track browser. |

## Screenshot Evidence

The WebP shows the shared review with the restored eight-track result.

## Timings

| Step | Timing |
|---|---:|
| Reload navigation | 261 ms |
| Settled result | < 1 s after reload |

## Handoff Notes

- Completed: FLT_15 is terminal `PASS`.
- Remaining unfinished coverage: FLT_16 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: Review tracks open; Tracks by quarter; exact Q1; eight matching tracks.
