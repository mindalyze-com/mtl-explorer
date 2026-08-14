# Packet: UXP_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: UXP_01.
- In scope: three warmed desktop core journeys, visible input feedback, main-thread responsiveness, every completed first-party application API request, pending/error checks, and post-third interaction.

## Prerequisites

- Required previous coverage IDs or run packets: ERR_02.
- Required app/data state: 12 imported tracks, Q1 active with 8/12 visible, Admin Processing Idle, data Current, services 3/3 available, helpers 2/2 ready.
- Required browser context: signed-in warmed 1280 x 720 desktop session on the required port-18080 origin.

## Allowed Mutations

- Allowed: pan/zoom; disable/re-enable Apply filter as the effective reset/apply cycle; navigate Statistics, search Jura, open #100000 tabs, open/close Planner.
- Not allowed: persist a new filter, plan, data mutation, upload, download, or fault injection.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| UXP_01 | Ran three consecutive map→Filter reset/apply→Statistics Overview/Tracks→Jura search→#100000 detail-tabs→Planner→map journeys, then repeated map and main-navigation probes. Concurrent DOM/screenshot probes measured first visible feedback and responsiveness; server completion logs captured every first-party API request. | Every input gives visible feedback within 200 ms; no measured main-thread stall exceeds 500 ms; every first-party API finishes within 2 s with expected status/UI; no unexpected pending/error or loss of interaction remains. | All 60 measured inputs passed: worst feedback 150 ms and worst probe gap 150 ms. All 171 completed requests across 15 routes returned 200; worst time was 478 ms. No console warn/error occurred, no busy UI remained, all journey checkpoints passed, and post-third pan/zoom/navigation stayed responsive. | PASS | [interaction timings](../assets/UXP_01-interactions.txt), [API timings](../assets/UXP_01-api-timings.txt), [final map](../assets/UXP_01-final.webp) |

## Issues

No issue found.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/UXP_01-interactions.txt](../assets/UXP_01-interactions.txt) | All 60 input-feedback and responsiveness-probe measurements, journey checkpoints, console result, and pending-state method. |
| [assets/UXP_01-api-timings.txt](../assets/UXP_01-api-timings.txt) | Route dictionary, per-route summary, and compact row for every one of 171 completed first-party requests. |
| [assets/UXP_01-final.webp](../assets/UXP_01-final.webp) | Populated, interactive final Q1 map after the third journey and final probes. |

## Screenshot Evidence

![Responsive populated map after the third journey](../assets/UXP_01-final.webp)

## Timings

| Measure | Timing |
|---|---:|
| Worst first visible feedback | 150 ms |
| Worst responsiveness-probe gap | 150 ms |
| Worst first-party API response | 478 ms |
| Completed first-party requests | 171 |
| API routes | 15 |
| Unexpected API status / over 2 s | 0 / 0 |
| Console warn/error in window | 0 |

## Handoff Notes

- Completed: UXP_01 is terminal `PASS`.
- Remaining unfinished coverage: RUN_CLEANUP only, after the mandatory finalization gate passes.
- Blocked or not applicable: browser `networkidle` is unsupported by this controller; the pending check instead used settled expected UI, no busy/spinner state, complete expected server requests, and zero console errors.
- State left for the next packet: `/mtl/`, Q1 active, 8/12 map, no open tool, warmed signed-in desktop session.

