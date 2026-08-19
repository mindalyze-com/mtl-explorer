# Packet: FLT_17

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FLT_17
- In scope: First Filter open in a clean browser context, automatic Important guidance, Back/Close, Got it, and repeat prevention.
- Out of scope: Returning-user compact guidance covered by FLT_18.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_16.
- Required app/data state: Same target data through a clean alternate origin with separate localStorage.
- Required browser context: New authenticated in-app browser tab.

## Allowed Mutations

- Allowed: Mark first-use guidance seen in the isolated clean origin.
- Not allowed: Affect the primary IP-origin Filter state.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FLT_17 | Opened Filter first time on a clean origin, inspected guidance/controls, selected Got it, closed and reopened Filter. | Guidance auto-opens with Important and full explanation; Back/Close work; Got it returns and prevents repeat. | All required text/controls rendered. Got it returned to overview. Second Filter open did not auto-open guidance or Important. | PASS | [assets/FLT_17-first-guidance.txt](../assets/FLT_17-first-guidance.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_17-first-guidance.txt](../assets/FLT_17-first-guidance.txt) | Clean-context provenance, first guidance contents/controls, Got it return, and repeat prevention. |

## Screenshot Evidence

Unavailable under ACC_04. Exact rendered headings, badge, controls, text sections, and second-open absence provide direct DOM evidence.

## Timings

| Step | Timing |
|---|---:|
| Clean-origin login | About 2 s |
| First guidance inspection | About 1 s |
| Got it and second open | About 2 s |

## Handoff Notes

- Completed: Clean-context automatic guidance, Important content, Got it, and repeat prevention.
- Remaining unfinished coverage: None for FLT_17.
- Blocked or not applicable: None.
- State left for the next packet: Clean-origin context is now a returning user; primary tab retains year 2013 filter state.
