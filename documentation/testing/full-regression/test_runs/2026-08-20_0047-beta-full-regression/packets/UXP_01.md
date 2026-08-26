# Packet: UXP_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: UXP_01
- In scope: Three warmed desktop journeys, visible feedback, main-thread stalls, first-party API timing/status/UI result, pending requests, console errors, and final interaction.

## Prerequisites

- Required previous coverage IDs or run packets: ERR_02 and all journey component packets.
- Required app/data state: Eight imported tracks; background jobs settled; healthy services.
- Required browser context: Signed-in warmed desktop session.

## Allowed Mutations

- Allowed: Recenter/zoom map, apply/reset disposable filter, search/open/close details, open/close Planner.
- Not allowed: Count tiles/static assets against the API budget or infer unavailable performance metrics.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| UXP_01 | Ran three required journeys; captured each visible state, server timing for every completed active-session first-party API request, and console logs; audited browser instrumentation. | Feedback <200 ms, stalls <=500 ms, APIs <2 s with expected results/status, no pending/error, final map/nav responsive. | All three journeys and final interaction passed; 123/123 APIs had expected status and max 353 ms; console errors 0. UI-only latency, main-thread stalls, and pending-request inventory are not exposed by the connected browser. | BLOCKED | [assets/UXP_01-actions.txt](../assets/UXP_01-actions.txt); [assets/UXP_01-api.txt](../assets/UXP_01-api.txt) |

## Issues

No new product issue; the remaining budgets cannot be measured with the connected browser diagnostics.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/UXP_01-actions.txt](../assets/UXP_01-actions.txt) | Three journeys, feedback states, upper-bound timings, console, and instrumentation constraints. |
| [assets/UXP_01-api.txt](../assets/UXP_01-api.txt) | Every active-session completed first-party API duration grouped by route/status. |

## Screenshot Evidence

A screenshot is insufficient for this packet and is blocked by ACC_04; timing/text evidence is linked above.

## Timings

| Step | Timing |
|---|---:|
| Complete capture window | 4m 16s |
| Slowest server API | 353 ms |

## Handoff Notes

- Completed: Three journeys, all server API completions/statuses, console audit, and final map/nav check.
- Remaining unfinished coverage: None for UXP_01.
- Blocked or not applicable: UI-only 200 ms feedback, maximum main-thread stall, and client pending-request inventory.
- State left for the next packet: Root map; eight tracks; Smart Base Filter; all services running.
