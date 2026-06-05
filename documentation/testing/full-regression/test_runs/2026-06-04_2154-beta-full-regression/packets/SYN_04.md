# Packet: SYN_04

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: SYN_04
- In scope: FIT conversion import freshness/cache behavior compared with native GPX import.
- Out of scope: Other coverage IDs except where noted as shared state/evidence.

## Prerequisites

- Required previous coverage IDs or run packets: FIT_01 through FIT_06 terminal and prior import/delete freshness evidence available.
- Required app/data state: Current run-state and packet set for this run.
- Required browser context: As specified by the coverage action.

## Allowed Mutations

- Allowed: Review completed FIT import/cache/freshness evidence and update SYN_04 packet/run-state.
- Not allowed: Collapse this ID into a parent row or skip required direct evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SYN_04 | Reviewed FIT import/indexer/freshness evidence and UI reload evidence for Activity.fit. | FIT conversion import changes freshness and cache state the same way a native GPX import does. | PASS: Activity.fit copied to the watched folder, indexed as track 100005 with SUCCESS, advanced freshness/index/track revisions, appeared after cache reload on map/stats/browser surfaces, opened in details, and supported original FIT and GPX export. | PASS | [assets/FIT_01-import-copy.txt](../assets/FIT_01-import-copy.txt); [assets/FIT_02-index-wait.txt](../assets/FIT_02-index-wait.txt); [assets/FIT_02-map-fit-indexed.webp](../assets/FIT_02-map-fit-indexed.webp); [assets/FIT_02-stats-fit-included.webp](../assets/FIT_02-stats-fit-included.webp); [assets/FIT_02-ui-api-summary.txt](../assets/FIT_02-ui-api-summary.txt); [assets/FIT_03-overview.webp](../assets/FIT_03-overview.webp); [assets/FIT_04-original-download.txt](../assets/FIT_04-original-download.txt); [assets/FIT_05-gpx-download-validation.txt](../assets/FIT_05-gpx-download-validation.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [assets/FIT_01-import-copy.txt](../assets/FIT_01-import-copy.txt) | Text/log evidence |
| [assets/FIT_02-index-wait.txt](../assets/FIT_02-index-wait.txt) | Text/log evidence |
| [assets/FIT_02-map-fit-indexed.webp](../assets/FIT_02-map-fit-indexed.webp) | Screenshot evidence |
| [assets/FIT_02-stats-fit-included.webp](../assets/FIT_02-stats-fit-included.webp) | Screenshot evidence |
| [assets/FIT_02-ui-api-summary.txt](../assets/FIT_02-ui-api-summary.txt) | Text/log evidence |
| [assets/FIT_03-overview.webp](../assets/FIT_03-overview.webp) | Screenshot evidence |
| [assets/FIT_04-original-download.txt](../assets/FIT_04-original-download.txt) | Text/log evidence |
| [assets/FIT_05-gpx-download-validation.txt](../assets/FIT_05-gpx-download-validation.txt) | Text/log evidence |

## Screenshot Evidence

![assets/FIT_02-map-fit-indexed.webp](../assets/FIT_02-map-fit-indexed.webp)
![assets/FIT_02-stats-fit-included.webp](../assets/FIT_02-stats-fit-included.webp)
![assets/FIT_03-overview.webp](../assets/FIT_03-overview.webp)

## Timings

| Step | Timing |
|---|---:|
| Evidence synthesis from completed FIT packets | ~5 seconds |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
