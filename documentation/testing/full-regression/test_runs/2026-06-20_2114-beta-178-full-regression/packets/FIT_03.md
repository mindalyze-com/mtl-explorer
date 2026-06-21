# Packet: FIT_03

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: FIT_03
- In scope: Verify FIT-backed track detail overview, graphs, quality, related tracks, events, mini-map, and track-point popup behavior.
- Out of scope: FIT original/download conversion checks; covered by FIT_04 and FIT_05.

## Prerequisites

- Required previous coverage IDs or run packets: FIT_02.
- Required app/data state: `Activity.fit` imported and indexed as `Track 100005`.
- Required browser context: authenticated desktop browser.

## Allowed Mutations

- Allowed: open FIT-backed track detail tabs and interact with the detail mini-map.
- Not allowed: import, delete, or edit tracks.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FIT_03 | Opened `/mtl/track/100005`, visited Overview, Graphs, Quality, Related Tracks, and Events, then clicked the FIT mini-map route line. Retested the original failed coordinate and the detected route-line center. | FIT-backed detail behaves like GPX-backed tracks: all detail tabs render, graphs are populated, quality data is shown, related/events views open, mini-map renders, and point clicks open point-level popups. | PASS after retest: detail tabs and mini-map rendered. The recorded failed click at `668,280` did not open a popup because it was about 64 px below the route line. Clicking the detected route-line center at `677.5,216.1875` opened a Track point popup with point, time, distance, elevation, speed, and elapsed metrics. `FIT-03-P2` is rejected as an off-target-click test artifact. | PASS | [assets/FIT_03-detail-tabs.txt](../assets/FIT_03-detail-tabs.txt); [assets/FIT_03-overview.webp](../assets/FIT_03-overview.webp); [assets/FIT_03-graphs.webp](../assets/FIT_03-graphs.webp); [assets/FIT_03-quality.webp](../assets/FIT_03-quality.webp); [assets/FIT_03-related.webp](../assets/FIT_03-related.webp); [assets/FIT_03-events.webp](../assets/FIT_03-events.webp); [assets/FIT_03-point-popup.webp](../assets/FIT_03-point-popup.webp); [assets/FIT_03-retest-point-popup.txt](../assets/FIT_03-retest-point-popup.txt); [assets/FIT_03-retest-point-popup.webp](../assets/FIT_03-retest-point-popup.webp); [assets/FIT_03-rejected-evidence.txt](../assets/FIT_03-rejected-evidence.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| FIT-03-P2 | P2 | FIT-backed detail mini-map point click does not open a point popup. | Import `Activity.fit`, open `/mtl/track/100005`, and click the visible mini-map route line. | A track-point popup appears with point-level metrics, as required for FIT-backed tracks. | REJECTED on 2026-06-20 retest: the recorded failed coordinate was off the route line; clicking the detected route-line center opened a Track point popup. | [assets/FIT_03-detail-tabs.txt](../assets/FIT_03-detail-tabs.txt); [assets/FIT_03-point-popup.webp](../assets/FIT_03-point-popup.webp); [assets/FIT_03-retest-point-popup.txt](../assets/FIT_03-retest-point-popup.txt); [assets/FIT_03-retest-point-popup.webp](../assets/FIT_03-retest-point-popup.webp); [assets/FIT_03-rejected-evidence.txt](../assets/FIT_03-rejected-evidence.txt) | Rejected; original failure was an off-target-click test artifact, not a product issue. |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FIT_03-detail-tabs.txt](../assets/FIT_03-detail-tabs.txt) | Text evidence for FIT detail tabs and point-popup attempts. |
| [assets/FIT_03-overview.webp](../assets/FIT_03-overview.webp) | FIT detail overview rendered. |
| [assets/FIT_03-graphs.webp](../assets/FIT_03-graphs.webp) | FIT detail graphs rendered with controls and charts. |
| [assets/FIT_03-quality.webp](../assets/FIT_03-quality.webp) | FIT detail quality tab showing successful import/index quality data. |
| [assets/FIT_03-related.webp](../assets/FIT_03-related.webp) | Related Tracks tab rendered for the FIT-backed track. |
| [assets/FIT_03-events.webp](../assets/FIT_03-events.webp) | Events tab rendered for the FIT-backed track. |
| [assets/FIT_03-point-popup.webp](../assets/FIT_03-point-popup.webp) | Mini-map after point-click attempts, showing no point popup. |
| [assets/FIT_03-retest-point-popup.txt](../assets/FIT_03-retest-point-popup.txt) | Retest click coordinates and popup text. |
| [assets/FIT_03-retest-point-popup.webp](../assets/FIT_03-retest-point-popup.webp) | Retest screenshot showing FIT detail mini-map point popup. |
| [assets/FIT_03-rejected-evidence.txt](../assets/FIT_03-rejected-evidence.txt) | Rejected-issue rationale and positive popup evidence. |

## Screenshot Evidence

![FIT detail overview](../assets/FIT_03-overview.webp)

![FIT detail graphs](../assets/FIT_03-graphs.webp)

![FIT detail quality](../assets/FIT_03-quality.webp)

![FIT detail related tracks](../assets/FIT_03-related.webp)

![FIT detail events](../assets/FIT_03-events.webp)

![FIT detail point popup attempt](../assets/FIT_03-point-popup.webp)

![FIT detail point popup retest](../assets/FIT_03-retest-point-popup.webp)

## Timings

| Step | Timing |
|---|---:|
| Detail tab navigation | ~45 seconds |
| Point-popup interaction attempts and retest | ~40 seconds |

## Handoff Notes

- Completed: FIT_03 is terminal and passes after retest.
- Remaining unfinished coverage: FIT_04 onward.
- Blocked or not applicable: `FIT-03-P2` is rejected as a product issue.
- State left for the next packet: FIT-backed track remains `Track 100005` at `/mtl/track/100005`.
