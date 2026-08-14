# Packet: FLT_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: FLT_01.
- In scope: persisted active filter and map count chip after reload.
- Out of scope: catalog and parameter edits.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_15.
- Required app/data state: Smart Base Filter active for all 12 tracks.
- Required browser context: desktop map and Filter sheet.

## Allowed Mutations

- Allowed: close, reload, and reopen Filter.
- Not allowed: change the filter.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FLT_01 | Closed Filter, reloaded, checked the map chip, and reopened Filter. | Saved filter remains active and appears as a chip. | The `12 Tracks` map chip appeared after reload; Filter reopened with 12 matching tracks and persisted Smart Base Filter configuration. | PASS | [persisted filter](../assets/FLT_01-persisted-filter.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_01-persisted-filter.txt](../assets/FLT_01-persisted-filter.txt) | Reloaded chip, current-result, and view state. |

## Screenshot Evidence

The exact chip text and persisted configuration are recorded as text.

## Timings

| Step | Timing |
|---|---:|
| Reload to chip | < 2 s |
| Reopen Filter | < 1 s |

## Handoff Notes

- Completed: FLT_01.
- Remaining unfinished coverage: FLT_02 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: Filter overview open with Smart Base Filter active and all 12 tracks.

