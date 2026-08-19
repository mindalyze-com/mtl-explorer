# Packet: IMP_09

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: IMP_09
- In scope: Correct-direction changes in totals, activity/period/ranking data, heatmap density, and track-browser summary.
- Out of scope: Exact domain recalculation algorithms.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_01 and IMP_05-IMP_08.
- Required app/data state: Five imported tracks loaded.
- Required browser context: Statistics, browser, and visual heatmap canvas.

## Allowed Mutations

- Allowed: Read totals and toggle/read map heatmap.
- Not allowed: Claim heatmap correctness from non-visual counters.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| IMP_09 | Compared empty and five-track Overview/browser totals and inspected activity breakdown, dated period rankings, highlights, and per-track descent; audited the remaining visual heatmap-density requirement. | All listed totals and summaries move in the correct direction, including visible heatmap density. | Track, distance, duration, energy, ascent, activity, period, ranking, and browser totals all changed from empty to non-zero coherent values. Heatmap-density correctness cannot be directly inspected because it is canvas-only and screenshots are unavailable. | BLOCKED | [assets/IMP_09-totals.txt](../assets/IMP_09-totals.txt); [assets/IMP_01-baseline.txt](../assets/IMP_01-baseline.txt); [assets/IMP_05-reload.txt](../assets/IMP_05-reload.txt); [assets/ACC_04-screenshot-capability.txt](../assets/ACC_04-screenshot-capability.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/IMP_09-totals.txt](../assets/IMP_09-totals.txt) | Exact before/after metrics and blocked heatmap child. |
| [assets/IMP_01-baseline.txt](../assets/IMP_01-baseline.txt) | Empty baseline. |
| [assets/IMP_05-reload.txt](../assets/IMP_05-reload.txt) | Post-import totals and names. |

## Screenshot Evidence

Blocked by ACC_04; that missing visual channel prevents direct heatmap-density verification.

## Timings

| Step | Timing |
|---|---:|
| Totals comparison | About 5 s |

## Handoff Notes

- Completed: All non-visual totals and summaries changed coherently.
- Remaining unfinished coverage: None; terminally blocked for heatmap-density visual evidence.
- Blocked or not applicable: Requires functional screenshot/visual targeting or semantic heatmap density data.
- State left for the next packet: Full five-GPX data set remains present; deletion is deferred until later checks no longer require it.
