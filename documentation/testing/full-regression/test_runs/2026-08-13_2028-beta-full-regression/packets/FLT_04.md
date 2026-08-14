# Packet: FLT_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: FLT_04.
- In scope: saved date, text, and geo parameters across reload.
- Out of scope: all geo drawing controls, covered next.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_03.
- Required app/data state: Activities by keyword view.
- Required browser context: Filter criteria and map.

## Allowed Mutations

- Allowed: set two dates, one keyword, and one rectangle, then reload.
- Not allowed: infer persistence from overview count alone.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FLT_04 | Saved from/to dates, keyword Lannion, and a rectangle; recorded values/result; reloaded and reopened criteria/area editor. | Date, text, and geo parameters save and reapply after reload. | All exact date/text values, one rectangle with matching displayed coordinates, four-criterion summary, and intentional 0/12 result restored after reload. | PASS | [parameter persistence](../assets/FLT_04-parameter-persistence.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_04-parameter-persistence.txt](../assets/FLT_04-parameter-persistence.txt) | Exact before/after values, area coordinates, summary, and result. |

## Screenshot Evidence

Exact restored form values and area text provide direct persistence evidence.

## Timings

| Step | Timing |
|---|---:|
| Save criteria and rectangle | < 2 min |
| Reload and rehydrate | < 2 s |

## Handoff Notes

- Completed: FLT_04.
- Remaining unfinished coverage: FLT_05 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: Criteria open with one persisted rectangle, date/text criteria, and intentional 0/12 result.

