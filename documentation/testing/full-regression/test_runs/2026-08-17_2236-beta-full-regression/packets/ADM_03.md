# Packet: ADM_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: ADM_03
- In scope: GPS/MEDIA indexer status, active/pending state, completed, failed,
  removed, percentages, totals, and explicit refresh over time.
- Out of scope: Manual rescan action behavior, covered by ADM_04.

## Prerequisites

- Required previous coverage IDs or run packets: ADM_02.
- Required app/data state: Existing completed/removed rows plus disposable
  valid and broken synthetic GPX uploads.
- Required browser context: Desktop Admin > Processing.

## Allowed Mutations

- Allowed: Upload a disposable broken synthetic GPX to create a real failure.
- Not allowed: Direct database writes or fabricated pending states.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ADM_03 | Compared cached GPS/MEDIA cards, uploaded valid/broken synthetic GPX files, and repeatedly used Refresh through active and terminal states. | Pending/running/completed/failed/removed states, percentages, and counts remain accurate and refresh over time. | GPS changed 18/28/64% to 19/29/65%, then showed 19 completed + 1 failed + 4 removed + 6 excluded = 30 at 63%. MEDIA stayed 4 + 2 = 6 at 66%. Live jobs exposed running/pending percentages and later advanced to done. Refresh timestamps advanced. | PASS | [assets/ADM_03-indexer-status.txt](../assets/ADM_03-indexer-status.txt); [assets/ADM_03-live-failed.jpg](../assets/ADM_03-live-failed.jpg) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/ADM_03-indexer-status.txt](../assets/ADM_03-indexer-status.txt) | Exact count, percentage, failure, live, and refresh transitions. |
| [assets/ADM_03-live-failed.jpg](../assets/ADM_03-live-failed.jpg) | GPS failed/removal counters and running/pending jobs in one Processing view. |
| [assets/ADM_03-broken.gpx](../assets/ADM_03-broken.gpx) | Intentionally truncated synthetic failure fixture. |
| [assets/ADM_03-malformed.gpx](../assets/ADM_03-malformed.gpx) | Synthetic invalid-coordinate behavior fixture. |

## Screenshot Evidence

- The Processing panel image shows reconciled GPS/MEDIA indexer counts and
  simultaneous running/pending background work.

## Timings

| Step | Timing |
|---|---:|
| Explicit status refresh | Under 700 ms |
| Broken GPX watcher result | About 9 s |
| Duplicate Finder pending to done | Under 30 s |

## Handoff Notes

- Completed: Indexer status accuracy and refresh transitions passed.
- Remaining unfinished coverage: None for ADM_03.
- Blocked or not applicable: Tiny file-indexer work completed between manual
  card refreshes; active state remained visible via Processing Live.
- State left for the next packet: Broken file 300035 remains FAILED and
  one-point track 100024 remains disposable for ADM_04 rescan behavior.
