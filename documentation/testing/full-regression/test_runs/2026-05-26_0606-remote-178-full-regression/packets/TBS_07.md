# Packet: TBS_07

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: TBS_07
- In scope: statistics correctness across empty, single-track, and many-track visible states.
- Out of scope: recalculating each metric independently from source GPX/FIT.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_01, FLT_06, TBS_06.
- Required app/data state: baseline empty state captured; current imported dataset available.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: use existing evidence from earlier packet states.
- Not allowed: change data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TBS_07 | Compared previously captured empty baseline, a one-track filtered stats view, and the current 10-track overview. | Stats are correct for empty dataset, a single track, and many tracks. | Empty baseline showed no imported tracks; filtered stats showed `Showing 1 of 10 tracks` with Jura-only totals; all-track overview showed 10 tracks, 1,262 km, 1d 03h, activity breakdown, highlights, and milestones. | PASS | `assets/IMP_01-stats-baseline.webp`, `assets/FLT_06-stats-filtered.webp`, `assets/TBS_06-overview-top.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/IMP_01-stats-baseline.webp | Empty dataset statistics baseline. |
| assets/FLT_06-stats-filtered.webp | One-track filtered statistics state. |
| assets/TBS_06-overview-top.webp | Many-track statistics overview. |

## Timings

| Step | Timing |
|---|---:|
| Cross-state stats evidence review | <1m |

## Handoff Notes

- Completed: TBS_07 terminal PASS.
- Remaining unfinished coverage: continue with TBS_08.
- Blocked or not applicable: none.
- State left for the next packet: Stats Overview open.
