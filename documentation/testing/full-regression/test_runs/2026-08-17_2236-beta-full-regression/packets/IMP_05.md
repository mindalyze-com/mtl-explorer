# Packet: IMP_05

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: IMP_05
- In scope: Freshness reload updates map, track browser, filter, and statistics without a browser restart.
- Out of scope: Per-file drilldown beyond visible names.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_04.
- Required app/data state: Server import complete; client intentionally out of sync.
- Required browser context: Signed-in desktop browser with freshness banner.

## Allowed Mutations

- Allowed: Use the in-app Reload action and navigate read-only views.
- Not allowed: Restart browser or server to obtain fresh data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| IMP_05 | Closed Admin, used the `New data available` banner's Reload action, then opened Statistics Tracks, Statistics Overview, and Filter. | Map, browser, filter, and statistics show the five new tracks after the in-app reload without a browser restart. | Map changed from the stale 2 Tracks to 5 Tracks; browser lists five named tracks totaling 1,043 km and 23h 31m; Overview reports the same five and non-zero totals; Filter reports 5 matching tracks. | PASS | [assets/IMP_05-reload.txt](../assets/IMP_05-reload.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/IMP_05-reload.txt](../assets/IMP_05-reload.txt) | Banner action and post-reload cross-view counts/totals/names. |

## Screenshot Evidence

Blocked by ACC_04; direct DOM status evidence is recorded.

## Timings

| Step | Timing |
|---|---:|
| Freshness reload to 5-track map | 5.4 s |
| Cross-view verification | About 15 s |

## Handoff Notes

- Completed: In-app reload propagated five imported tracks across map/browser/filter/statistics.
- Remaining unfinished coverage: None for IMP_05.
- Blocked or not applicable: Screenshot capture blocked under ACC_04.
- State left for the next packet: Filter sheet open with 5 matching tracks.
