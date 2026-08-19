# Packet: SRC_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: SRC_04
- In scope: Empty-query and no-result feedback.
- Out of scope: Successful results and selection.

## Prerequisites

- Required previous coverage IDs or run packets: SRC_03.
- Required app/data state: No active search marker.
- Required browser context: Desktop Search sheet.

## Allowed Mutations

- Allowed: Enter an impossible query and clear it.
- Not allowed: Select or fabricate a result.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SRC_04 | Opened Search, entered an impossible query, then cleared it on the fixed build at desktop and mobile sizes. | Empty and no-result queries show clear messages. | No-result still showed No matches; empty and cleared input showed Search for a city, peak, or area without issuing an empty request. | FIXED | [details](../assets/SRC_04-remediation.txt); [desktop](../assets/SRC_04-fixed-desktop.webp); [mobile](../assets/SRC_04-fixed-mobile.webp) |

## Issues

| Issue ID | Severity | Title | Reproduction | Expected | Actual | Evidence | Impact |
|---|---|---|---|---|---|---|---|
| FR-013 | P2 | Empty location search has no empty-state prompt. | Open Search, type any query, then activate Clear search. | Empty query results region shows a clear prompt or message. | Input placeholder returns, but the results region is blank; only sorting controls remain. | [assets/SRC_04-empty-states.txt](../assets/SRC_04-empty-states.txt); [assets/SRC_04-empty.jpg](../assets/SRC_04-empty.jpg) | New users receive no explanation of what to enter or why the sheet is empty. |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SRC_04-empty-states.txt](../assets/SRC_04-empty-states.txt) | Exact no-result and empty-query observations. |
| [assets/SRC_04-no-matches.jpg](../assets/SRC_04-no-matches.jpg) | Passing no-result `No matches` state. |
| [assets/SRC_04-empty.jpg](../assets/SRC_04-empty.jpg) | Failing blank empty-query state. |

## Screenshot Evidence

- The paired images make the gap explicit: the impossible query has `No matches`; the cleared empty query has no corresponding message.

## Timings

| Step | Timing |
|---|---:|
| No-result query settlement | About 1 s |
| Clear to empty state | Under 300 ms |

## Handoff Notes

- Completed: Both SRC_04 branches.
- Remaining unfinished coverage: None for SRC_04.
- Blocked or not applicable: None.
- State left for the next packet: Empty Search sheet remains open; no marker or result selected.

## Remediation Verification

- Finding FR-013 is `FIXED`: Search now provides a neutral empty-state prompt.
- Loading and error messages continue to take precedence.
