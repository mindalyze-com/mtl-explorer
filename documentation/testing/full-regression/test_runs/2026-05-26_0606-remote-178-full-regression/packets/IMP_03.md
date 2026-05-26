# Packet: IMP_03

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: IMP_03
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
| IMP_03 | Monitored live watcher and indexing logs after copy. | Indexing finishes; if live watch misses files, Rescan GPS is triggered and recorded. | Live watcher detected all five CREATE events and all five files completed `status=SUCCESS`; manual Rescan GPS was not required. | PASS | `assets/IMP_03-index-monitor.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/IMP_03-index-monitor.txt | Cropped watcher create and SUCCESS lines under 5 KB. |

## Timings

| Step | Timing |
|---|---:|
| IMP_03 execution | 16s |

## Handoff Notes

- Completed: IMP_03 terminal PASS.
- Remaining unfinished coverage: continue queue.
- Blocked or not applicable: none.
- State left for the next packet: five GPX tracks imported and visible; FIT not imported yet; no deletion performed.
