# Packet: IMP_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: IMP_04
- In scope: Five completed sources, no unexpected GPS failure, freshness change, and settled background jobs.
- Out of scope: Applying the freshness reload.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_03.
- Required app/data state: GPS 5/5 imported; background jobs allowed to finish.
- Required browser context: Admin Processing and Data status.

## Allowed Mutations

- Allowed: Wait and refresh user-facing Admin status.
- Not allowed: Reload data before capturing stale client/server revisions.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| IMP_04 | Refresh Admin until GPS and three track jobs settle; record freshness banner and server/client revision details. | Five sources complete, no unexpected GPS failures, freshness changes, and Duplicate Finder/Exploration Score settle. | GPS is done 5/5; Duplicate Finder, Activity Classifier, and Exploration Score are done 5/5; no failed state appears; server revisions advanced from index/tracks/geometry 0 to 15/30/30 and the stale banner is visible. | PASS | [assets/IMP_04-settled.txt](../assets/IMP_04-settled.txt); [assets/IMP_03-indexing.txt](../assets/IMP_03-indexing.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/IMP_04-settled.txt](../assets/IMP_04-settled.txt) | Settled jobs and pre-reload freshness-token change. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; exact visible Admin state is recorded in text.

## Timings

| Step | Timing |
|---|---:|
| Background jobs after GPS completion | <2 min |

## Handoff Notes

- Completed: Five source rows complete, background jobs settled, freshness changed, no failure visible.
- Remaining unfinished coverage: None for IMP_04.
- Blocked or not applicable: None.
- State left for the next packet: Browser is intentionally stale with Reload available; use it in IMP_05.
