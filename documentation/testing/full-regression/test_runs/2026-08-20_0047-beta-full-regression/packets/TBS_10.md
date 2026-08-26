# Packet: TBS_10

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TBS_10
- In scope: Navigation/filter/highlight behavior from a Statistics entry.
- Out of scope: Excluded-highlight counts, covered by TBS_11.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_09.
- Required app/data state: Populated Statistics Overview.
- Required browser context: Overview highlight cards.

## Allowed Mutations

- Allowed: Open a highlight drilldown and select its track.
- Not allowed: Change highlight curation.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TBS_10 | Activate Longest track, inspect drilldown, and select Mosel. | Entry opens relevant context and matching track. | One-track Longest drilldown opened; its card navigated to matching track 100001 details. | PASS | [assets/TBS_10-stats-entry-navigation.txt](../assets/TBS_10-stats-entry-navigation.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_10-stats-entry-navigation.txt](../assets/TBS_10-stats-entry-navigation.txt) | Entry, drilldown, card, route, and detail identity. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible drilldown and details states are linked above.

## Timings

| Step | Timing |
|---|---:|
| Highlight entry and track navigation | 2 min |

## Handoff Notes

- Completed: Statistics entry context and track navigation.
- Remaining unfinished coverage: None for TBS_10.
- Blocked or not applicable: None.
- State left for the next packet: Mosel track 100001 details open from Longest track drilldown.
