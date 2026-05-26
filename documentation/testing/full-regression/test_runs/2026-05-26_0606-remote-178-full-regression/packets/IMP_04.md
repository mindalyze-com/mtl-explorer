# Packet: IMP_04

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: IMP_04
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
| IMP_04 | Verified upload/index status and background job settlement from logs and Admin Jobs. | All five source files complete; no unexpected GPS failures; freshness changes; background jobs settle. | All five source files reached SUCCESS; Admin Jobs showed quiet/idle processing, no visible GPS failures, and data freshness moved out of sync after import. | PASS | `assets/IMP_03-index-monitor.txt`, `assets/IMP_04-jobs-after-import.webp`, `assets/IMP_04-freshness-out-of-sync.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/IMP_04-jobs-after-import.webp | Admin Jobs after import. |
| assets/IMP_04-freshness-out-of-sync.webp | Freshness token after import before client refresh. |
| assets/IMP_03-index-monitor.txt | Import SUCCESS lines. |

## Timings

| Step | Timing |
|---|---:|
| IMP_04 execution | <1m |

## Handoff Notes

- Completed: IMP_04 terminal PASS.
- Remaining unfinished coverage: continue queue.
- Blocked or not applicable: none.
- State left for the next packet: five GPX tracks imported and visible; FIT not imported yet; no deletion performed.
