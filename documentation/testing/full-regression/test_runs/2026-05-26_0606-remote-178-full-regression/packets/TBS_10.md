# Packet: TBS_10

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: TBS_10
- In scope: clicking a statistics entry and confirming the UI navigates, filters, or highlights the expected result.
- Out of scope: full highlight drilldown workflow, covered by TBS_11.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_06.
- Required app/data state: Stats Overview visible with highlight entries.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: click a stats highlight entry.
- Not allowed: change data or highlight exclusion state.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TBS_10 | Clicked the `Longest track` statistics highlight for Moselradweg. | Clicking a stats entry navigates, filters, or highlights as expected. | The clicked highlight became active and opened a ranked `Longest track` drilldown list headed by Moselradweg, with matching ranked tracks and per-track action rows. | PASS | `assets/TBS_10-highlight-before.webp`, `assets/TBS_10-highlight-drilldown.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/TBS_10-highlight-before.webp | Stats Overview before highlight click. |
| assets/TBS_10-highlight-drilldown.webp | Active highlight drilldown after click. |

## Timings

| Step | Timing |
|---|---:|
| Highlight click | <2m |

## Handoff Notes

- Completed: TBS_10 terminal PASS.
- Remaining unfinished coverage: continue with TBS_11.
- Blocked or not applicable: none.
- State left for the next packet: Stats Overview remains open with the `Longest track` drilldown visible.
