# Packet: DEL_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: DEL_04
- In scope: Verify the remaining imported tracks still display and open after deletion.
- Out of scope: Pre-delete track checks already covered by IMP_05-IMP_07.

## Prerequisites

- Required previous coverage IDs or run packets: DEL_01-DEL_03 executed.
- Required app/data state: Two tracks removed and remaining set synchronized.
- Required browser context: Signed-in map and track browser.

## Allowed Mutations

- Allowed: Read-only searches and detail opens.
- Not allowed: Edit remaining tracks.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DEL_04 | Search and open Jura, Mosel, and Lannion after deletion. | Every remaining imported track displays and opens matching details. | All three rows were present and opened matching IDs/names with Overview selected. | PASS | [assets/DEL_04-remaining-tracks.txt](../assets/DEL_04-remaining-tracks.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DEL_04-remaining-tracks.txt](../assets/DEL_04-remaining-tracks.txt) | Exact search, row, route, and detail identities. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible row and detail identities are linked above.

## Timings

| Step | Timing |
|---|---:|
| Search and open three remaining tracks | 3 min |

## Handoff Notes

- Completed: Display and detail-open checks for every remaining original GPX import.
- Remaining unfinished coverage: None for DEL_04.
- Blocked or not applicable: None.
- State left for the next packet: Lannion track 100004 details open; seven-track synchronized dataset.
