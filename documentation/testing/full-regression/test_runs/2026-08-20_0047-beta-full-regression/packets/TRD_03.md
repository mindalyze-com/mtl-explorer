# Packet: TRD_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TRD_03
- In scope: Repeated Overview, Graphs, Quality, Related, and Events tab switching.
- Out of scope: Individual chart controls, covered by TRD_04-TRD_06.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_01 and TRD_02.
- Required app/data state: FIT-backed track 100005 retained.
- Required browser context: Authenticated desktop track details.

## Allowed Mutations

- Allowed: Switch tabs and inspect current panel state.
- Not allowed: Change or save track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_03 | Cycle through Graphs, Quality, Related, Events, and Overview twice, then return to Graphs and inspect load logs. | Each selected tab stays populated without reload loops, state loss, or a blank panel. | Every tab was selected and populated on the second cycle. Graph settings remained Time, Range, and 350 points; one details load and one chart fetch occurred for the mounted track. | PASS | [assets/TRD_03-tab-cycle.txt](../assets/TRD_03-tab-cycle.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_03-tab-cycle.txt](../assets/TRD_03-tab-cycle.txt) | Two-pass tab cycle, retained settings, and console load count. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible tab and panel states are linked above.

## Timings

| Step | Timing |
|---|---:|
| Two complete tab cycles and log review | 2 min |

## Handoff Notes

- Completed: All required detail tabs switched repeatedly with populated retained state.
- Remaining unfinished coverage: None for TRD_03.
- Blocked or not applicable: None.
- State left for the next packet: Graphs selected on FIT track 100005; healthy app.
