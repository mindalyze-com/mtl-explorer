# Packet: TRD_05

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: TRD_05
- In scope: Verify graph controls: time/distance x-axis toggle, range band toggle, point-count slider, and graph-height slider.
- Out of scope: Other coverage IDs except where noted as shared state/evidence.

## Prerequisites

- Required previous coverage IDs or run packets: Previous queue rows terminal or explicitly not required.
- Required app/data state: Current run-state and packet set for this run.
- Required browser context: As specified by the coverage action.

## Allowed Mutations

- Allowed: Read-only verification and packet/run-state updates.
- Not allowed: Collapse this ID into a parent row or skip required direct evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_05 | Clicked Distance, toggled Range off, changed point count down and back up, then increased and restored graph height. | Graph controls update charts without layout breakage. | Distance axis became active; Range toggled off; point count changed 350 -> 325 -> 350; first chart height changed 240 -> 250 -> 240 without layout breakage. | PASS | [assets/TRD_05-axis-distance.webp](../assets/TRD_05-axis-distance.webp); [assets/TRD_05-range-off.webp](../assets/TRD_05-range-off.webp); [assets/TRD_05-point-count-restored.webp](../assets/TRD_05-point-count-restored.webp); [assets/TRD_05-height-bigger.webp](../assets/TRD_05-height-bigger.webp); [assets/TRD_04_06-graphs-controls-hover-summary.txt](../assets/TRD_04_06-graphs-controls-hover-summary.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_05-axis-distance.webp](../assets/TRD_05-axis-distance.webp) | Screenshot evidence |
| [assets/TRD_05-range-off.webp](../assets/TRD_05-range-off.webp) | Screenshot evidence |
| [assets/TRD_05-point-count-restored.webp](../assets/TRD_05-point-count-restored.webp) | Screenshot evidence |
| [assets/TRD_05-height-bigger.webp](../assets/TRD_05-height-bigger.webp) | Screenshot evidence |
| [assets/TRD_04_06-graphs-controls-hover-summary.txt](../assets/TRD_04_06-graphs-controls-hover-summary.txt) | Text/log evidence |

## Screenshot Evidence

![assets/TRD_05-axis-distance.webp](../assets/TRD_05-axis-distance.webp)
![assets/TRD_05-range-off.webp](../assets/TRD_05-range-off.webp)
![assets/TRD_05-point-count-restored.webp](../assets/TRD_05-point-count-restored.webp)
![assets/TRD_05-height-bigger.webp](../assets/TRD_05-height-bigger.webp)

## Timings

| Step | Timing |
|---|---:|
| Packet execution | <1 minute |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
