# Packet: MAP_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: MAP_03.
- In scope: map reaction to the required import/freshness flow.
- Out of scope: Filter and Statistics invalidation, recorded separately as IMP-05-P1.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_01-IMP_05 and MAP_02.
- Required app/data state: preserved empty-to-five import evidence from this run.
- Required browser context: the same browser remained open across import and freshness acceptance.

## Allowed Mutations

- Allowed: reuse direct evidence from the required data-change packet.
- Not allowed: substitute a full browser restart for the freshness action.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_03 | Reviewed the direct empty→five import sequence in which the existing browser accepted the `New data available` Reload action. | Newly imported tracks appear on the map without a full browser restart. | Four seconds after the in-app Reload action, the existing main map changed from zero to five rendered tracks. No browser restart or normal page reload occurred before this map evidence. | PASS | [assets/IMP_05-map.webp](../assets/IMP_05-map.webp); [assets/IMP_05-reload-result.txt](../assets/IMP_05-reload-result.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/IMP_05-map.webp](../assets/IMP_05-map.webp) | Five new track overlays after the in-app freshness action. |
| [assets/IMP_05-reload-result.txt](../assets/IMP_05-reload-result.txt) | Exact action ordering; also records the separate stale Filter/Stats defect. |

## Screenshot Evidence

![Imported tracks after freshness acceptance](../assets/IMP_05-map.webp)

## Timings

| Step | Timing |
|---|---:|
| Freshness action to settled five-track map | 4 s |

## Handoff Notes

- Completed: required import-to-map freshness behavior.
- Remaining unfinished coverage: MAP_04 onward.
- Blocked or not applicable: none; IMP-05-P1 affects other surfaces but not this narrower map result.
- State left for the next packet: current 12-track Statistics view remains open; historical evidence is unchanged.
