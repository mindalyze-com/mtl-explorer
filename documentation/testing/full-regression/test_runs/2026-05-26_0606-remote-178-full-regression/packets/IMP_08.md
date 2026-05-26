# Packet: IMP_08

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: IMP_08
- In scope: five-GPX import/index/map/stats flow.
- Out of scope: FIT import and delete-two-track flow unless referenced as later prerequisites.

## Prerequisites

- Required previous coverage IDs or run packets: RUN_SETUP, DAT source packets, IMP_01 baseline.
- Required app/data state: fresh app with public GPX files staged outside watched folder before IMP_02.
- Required browser context: desktop signed-in browser.

## Allowed Mutations

- Allowed: copy GPX files into `./data/gpx`, wait for indexing, use freshness reload, open UI panels, click tracks.
- Not allowed: delete source files or import FIT files in IMP packets.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| IMP_08 | Compared baseline and post-import counts. | Statistics count increases by five unless a source legitimately splits, with mapping recorded. | Track count increased from 0 to 5; each GPX source produced exactly one imported track ID (100000-100004). | PASS | `assets/IMP_01-baseline-summary.txt`, `assets/IMP_06-imported-track-mapping.txt`, `assets/IMP_05-stats-after-import.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/IMP_01-baseline-summary.txt | Pre-import 0-track baseline. |
| assets/IMP_06-imported-track-mapping.txt | Five one-to-one imported tracks. |
| assets/IMP_05-stats-after-import.webp | Post-import 5-track stats. |

## Timings

| Step | Timing |
|---|---:|
| IMP_08 execution | <1m |

## Handoff Notes

- Completed: IMP_08 terminal PASS.
- Remaining unfinished coverage: continue queue.
- Blocked or not applicable: none.
- State left for the next packet: five GPX tracks imported and visible; FIT not imported yet; no deletion performed.
