# Packet: MAP_09

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MAP_09
- In scope: Click an area where several tracks overlap; selection list appears; picking one opens details.
- Out of scope: Other coverage IDs except where noted as shared state/evidence.

## Prerequisites

- Required previous coverage IDs or run packets: Imported overlapping GPX tracks present during IMP_07 direct map-click pass.
- Required app/data state: Current run-state and packet set for this run.
- Required browser context: As specified by the coverage action.

## Allowed Mutations

- Allowed: Use prior overlap chooser evidence and update packet/run-state.
- Not allowed: Collapse this ID into a parent row or skip required direct evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_09 | Reviewed the overlap click on the VoieVerte/Mosel area and subsequent selection of VoieVerte. | Clicking overlapping tracks shows a selection list and selecting one opens that track details. | The overlap click displayed a chooser with voie verte and Moselradweg entries; selecting VoieVerte opened track #100003 details. | PASS | [assets/IMP_07-try-voie1.webp](../assets/IMP_07-try-voie1.webp); [assets/IMP_07-try-voie1.txt](../assets/IMP_07-try-voie1.txt); [assets/IMP_07-select-VoieVerte.webp](../assets/IMP_07-select-VoieVerte.webp); [assets/IMP_07-select-VoieVerte.txt](../assets/IMP_07-select-VoieVerte.txt); [assets/IMP_07-precise-click-attempts.txt](../assets/IMP_07-precise-click-attempts.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [assets/IMP_07-try-voie1.webp](../assets/IMP_07-try-voie1.webp) | Screenshot evidence |
| [assets/IMP_07-try-voie1.txt](../assets/IMP_07-try-voie1.txt) | Text/log evidence |
| [assets/IMP_07-select-VoieVerte.webp](../assets/IMP_07-select-VoieVerte.webp) | Screenshot evidence |
| [assets/IMP_07-select-VoieVerte.txt](../assets/IMP_07-select-VoieVerte.txt) | Text/log evidence |
| [assets/IMP_07-precise-click-attempts.txt](../assets/IMP_07-precise-click-attempts.txt) | Text/log evidence |

## Screenshot Evidence

![assets/IMP_07-try-voie1.webp](../assets/IMP_07-try-voie1.webp)
![assets/IMP_07-select-VoieVerte.webp](../assets/IMP_07-select-VoieVerte.webp)

## Timings

| Step | Timing |
|---|---:|
| Evidence review | <1 minute |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
