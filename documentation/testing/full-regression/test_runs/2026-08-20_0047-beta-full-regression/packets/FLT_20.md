# Packet: FLT_20

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FLT_20
- In scope: Shared Review/Stats track-browser search, summary, table/cards, sorting, pagination, selection, and details.
- Out of scope: Sheet detents, covered by FLT_21.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_19.
- Required app/data state: Active eight-track year filter.
- Required browser context: Authenticated desktop Filter and Statistics; narrow emulation unavailable.

## Allowed Mutations

- Allowed: Sort, search, select a row, open details, and navigate to Stats Tracks.
- Not allowed: Edit track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FLT_20 | Exercise Review tracks and recheck Statistics Tracks. | Both use the same complete responsive track browser. | Desktop shared features passed, including search, sorting, pagination state, row selection, and details; narrow cards could not be established. | BLOCKED | [assets/FLT_20-shared-track-browser.txt](../assets/FLT_20-shared-track-browser.txt); [packets/ACC_04.md](ACC_04.md) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_20-shared-track-browser.txt](../assets/FLT_20-shared-track-browser.txt) | Desktop shared-component controls and exact mobile blocker. |

## Screenshot Evidence

Screenshot capture and narrow viewport emulation are BLOCKED in ACC_04.

## Timings

| Step | Timing |
|---|---:|
| Filter Review desktop feature pass | 5 min |
| Statistics Tracks recheck | 2 min |
| Narrow capability audit | 1 min |

## Handoff Notes

- Completed: Desktop Review/Stats shared track-browser feature pass.
- Remaining unfinished coverage: None; responsive narrow cards are terminal BLOCKED.
- Blocked or not applicable: Narrow viewport validation (ACC_04).
- State left for the next packet: Statistics Tracks with eight filtered rows.
