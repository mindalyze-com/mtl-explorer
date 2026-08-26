# Packet: MED_24

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_24
- In scope: Immediate same-bounds marker projection after manual-location set and clear, plus no-store response policy.
- Out of scope: General map pan/cluster behavior, covered by MED_02 and MED_27.

## Prerequisites

- Required previous coverage IDs or run packets: MED_23 cleanup.
- Required app/data state: Media 400002 resolved by its selected track correlation with no manual row.
- Required browser context: Authenticated app; network checks use a separate authenticated disposable session.

## Allowed Mutations

- Allowed: Set and immediately clear one manual location through production endpoints.
- Not allowed: Wait for a rescan, reload between mutation and GET, or retain the manual row.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_24 | GET one bounds URL, set manual point, repeat GET immediately, clear, repeat GET immediately. | Marker coordinates change and restore immediately; every response is `Cache-Control: no-store`. | Same URL returned 46.94809/7.44751, then 46.94920/7.44920 immediately after set, then the original point immediately after clear. All three were 200 with no-store. | PASS | [assets/MED_24-immediate-bounds.txt](../assets/MED_24-immediate-bounds.txt) |

## Issues

None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_24-immediate-bounds.txt](../assets/MED_24-immediate-bounds.txt) | Exact request sequence, response coordinates, timings, headers, and final state. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; exact network marker responses and cleanup state are linked above.

## Timings

| Step | Timing |
|---|---:|
| Baseline bounds GET | 4.102 ms |
| Immediate post-set GET | 8.792 ms |
| Immediate post-clear GET | 4.596 ms |

## Handoff Notes

- Completed: Immediate set/read/clear/read path, marker coordinate equality, cache header, and cleanup.
- Remaining unfinished coverage: None for MED_24.
- Blocked or not applicable: Screenshot evidence remains blocked by ACC_04.
- State left for the next packet: No manual/correction/work rows; media 400002 restored to TRACK_INTERPOLATED at its baseline point.
