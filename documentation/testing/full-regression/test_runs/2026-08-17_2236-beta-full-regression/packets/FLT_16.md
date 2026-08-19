# Packet: FLT_16

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FLT_16
- In scope: Legend group hiding affects map but not statistics; global category change resets temporary hiding.
- Out of scope: General legend behavior covered by FLT_07.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_15 and FLT_07.
- Required app/data state: Exact year selection 2013+2021.
- Required browser context: Main map legend, Statistics Overview, and Included categories.

## Allowed Mutations

- Allowed: Temporarily hide 2013, then change global selection to 2013 only.
- Not allowed: Leave 2013 temporarily hidden after the global selection change.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FLT_16 | Hid 2013 from legend, checked Statistics, then changed global categories to 2013 only. | Map visibility changes while stats do not; global selection reload resets temporary hiding. | Toolbar changed 2→1 while Statistics stayed 2 / 29.5 km. Global selection then restored 2013 to Hide/pressed and produced 1 visible track, proving hidden state reset. | PASS | [assets/FLT_16-legend-statistics.txt](../assets/FLT_16-legend-statistics.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_16-legend-statistics.txt](../assets/FLT_16-legend-statistics.txt) | Map/Statistics counts, legend names/states, and global-selection reset proof. |

## Screenshot Evidence

Unavailable under ACC_04. Exact control names, pressed states, visible counts, and Statistics totals provide direct DOM evidence.

## Timings

| Step | Timing |
|---|---:|
| Hide and Statistics check | About 5 s |
| Global selection change/reset | About 3 s |

## Handoff Notes

- Completed: Map-only hiding, Statistics independence, and reset on global selection change.
- Remaining unfinished coverage: None for FLT_16.
- Blocked or not applicable: None.
- State left for the next packet: Filter open; Tracks by year with exact 2013-only selection and the year visible on map.
