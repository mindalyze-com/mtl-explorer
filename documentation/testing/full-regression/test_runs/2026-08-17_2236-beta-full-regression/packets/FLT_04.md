# Packet: FLT_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FLT_04
- In scope: Date, text, and geographic criteria save and reapply after reload.
- Out of scope: Complete shape-editor controls covered by FLT_05.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_03.
- Required app/data state: Activities by keyword active.
- Required browser context: Filter criteria and map drawing mode.

## Allowed Mutations

- Allowed: Save one date, keyword, and circle, reload, then reset all criteria.
- Not allowed: Leave restrictive criteria after this packet.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FLT_04 | Set From 2026-08-17, Keyword `Synthetic`, drew a circle, reloaded `/mtl/filter`, reopened Criteria, and then reset. | Date, text, and geo values persist and reapply after reload. | All three criteria remained active. Exact date and keyword values returned, Area reported 1 area/Edit areas, and the same empty result reapplied. Reset restored empty controls, No area, and 15 tracks. | PASS | [assets/FLT_04-persistence.txt](../assets/FLT_04-persistence.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_04-persistence.txt](../assets/FLT_04-persistence.txt) | Saved, reloaded, and reset parameter values and result state. |

## Screenshot Evidence

Unavailable under ACC_04. Exact control values, criteria count, area count/action, and result state provide direct DOM evidence.

## Timings

| Step | Timing |
|---|---:|
| Enter date/text and draw circle | About 4 s |
| Reload and reopen criteria | About 2 s |
| Reset cleanup | About 1 s |

## Handoff Notes

- Completed: Persistence and reapplication of date, text, and geo criteria.
- Remaining unfinished coverage: None for FLT_04.
- Blocked or not applicable: None.
- State left for the next packet: Criteria open and clean; Activities by keyword active with all fifteen tracks.
