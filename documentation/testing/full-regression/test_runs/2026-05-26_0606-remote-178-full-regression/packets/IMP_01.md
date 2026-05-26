# Packet: IMP_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: IMP_01
- In scope: pre-import baseline for map count, track/stat totals, data freshness token, and GPS/indexer status.
- Out of scope: importing or indexing source files.

## Prerequisites

- Required previous coverage IDs or run packets: RUN_SETUP; public data staged by DAT packets.
- Required app/data state: fresh quick-install app before any test import.
- Required browser context: desktop browser signed in.

## Allowed Mutations

- Allowed: open Stats/Admin panels and capture evidence.
- Not allowed: copy files into watched folder or trigger rescans.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| IMP_01 | Captured map, Stats overview, Admin workspace/jobs, and Freshness panels before import. | Baseline map count, browser/stat totals, freshness token, and indexer state are recorded before data mutations. | Map showed `0 Tracks`; stats showed no matching tracks; Admin jobs were done with 0 totals and routing/location-search ready; freshness token was in sync with `index:0`, `media:0`, `track_geometry:0`, `tracks:0`. | PASS | `assets/RUN_SETUP-empty-map.webp`, `assets/IMP_01-stats-baseline.webp`, `assets/IMP_01-admin-baseline.webp`, `assets/IMP_01-jobs-baseline.webp`, `assets/IMP_01-freshness-baseline.webp`, `assets/IMP_01-baseline-summary.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/RUN_SETUP-empty-map.webp | Empty map baseline. |
| assets/IMP_01-stats-baseline.webp | Empty statistics baseline. |
| assets/IMP_01-admin-baseline.webp | Admin workspace baseline. |
| assets/IMP_01-jobs-baseline.webp | Indexer/job status baseline. |
| assets/IMP_01-freshness-baseline.webp | Data freshness baseline token. |
| assets/IMP_01-baseline-summary.txt | Compact text summary of baseline values. |

## Timings

| Step | Timing |
|---|---:|
| Baseline browser capture | 3m |

## Handoff Notes

- Completed: IMP_01 terminal PASS.
- Remaining unfinished coverage: continue with IMP_02 import.
- Blocked or not applicable: none.
- State left for the next packet: app remains at 0 tracks; source files are still staged outside watched import folder.
