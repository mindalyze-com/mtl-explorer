# Packet: SGN_09

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: SGN_09
- In scope: Browser Back/Forward navigation between distinct application views.
- Out of scope: View-specific feature behavior.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_08.
- Required app/data state: Healthy signed-in app with populated statistics and ready planner.
- Required browser context: In-app browser with normal history controls.

## Allowed Mutations

- Allowed: Navigate through visible Stats and Planner controls; use browser Back and Forward.
- Not allowed: Change server data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_09 | Open Stats, then Planner; use browser Back and Forward. | Each history action restores its view without errors. | Back restored `/mtl/stats` with populated statistics; Forward restored `/mtl/plan` with ready BRouter/planning content; no visible error state appeared. | PASS | [assets/SGN_09-history.txt](../assets/SGN_09-history.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SGN_09-history.txt](../assets/SGN_09-history.txt) | View URLs, view-specific content, history direction, timings, and error checks. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible view states are recorded in linked evidence.

## Timings

| Step | Timing |
|---|---:|
| Browser Back to Stats | 10.888 s including browser wait |
| Browser Forward to Planner | 10.859 s including browser wait |

## Handoff Notes

- Completed: Browser Back/Forward view restoration.
- Remaining unfinished coverage: None for SGN_09.
- Blocked or not applicable: None.
- State left for the next packet: Planner view open; server and browser data healthy.
