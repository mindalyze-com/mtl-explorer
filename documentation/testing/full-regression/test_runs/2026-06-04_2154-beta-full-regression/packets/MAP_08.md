# Packet: MAP_08

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MAP_08
- In scope: Click a single track and verify it highlights/details open.
- Out of scope: Other coverage IDs except where noted as shared state/evidence.

## Prerequisites

- Required previous coverage IDs or run packets: Imported GPX tracks present during IMP_07 direct map-click pass.
- Required app/data state: Current run-state and packet set for this run.
- Required browser context: As specified by the coverage action.

## Allowed Mutations

- Allowed: Use prior direct map-click evidence and update packet/run-state.
- Not allowed: Collapse this ID into a parent row or skip required direct evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_08 | Reviewed direct map-click evidence from IMP_07, where individual track clicks opened the corresponding details. | Clicking a single rendered track highlights/selects it and opens track details. | Single-track map clicks opened detail routes for imported tracks, including Lannion (#100004), Mosel (#100002), Jura (#100000), Vitry (#100001), and VoieVerte (#100003) during the map-click pass. | PASS | [assets/IMP_07-click-Lannion_Plestin1.webp](../assets/IMP_07-click-Lannion_Plestin1.webp); [assets/IMP_07-click-Lannion_Plestin1.txt](../assets/IMP_07-click-Lannion_Plestin1.txt); [assets/IMP_07-try-mosel4.webp](../assets/IMP_07-try-mosel4.webp); [assets/IMP_07-try-mosel4.txt](../assets/IMP_07-try-mosel4.txt); [assets/IMP_07-try-jura2.webp](../assets/IMP_07-try-jura2.webp); [assets/IMP_07-try-jura2.txt](../assets/IMP_07-try-jura2.txt); [assets/IMP_07-precise-click-attempts.txt](../assets/IMP_07-precise-click-attempts.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [assets/IMP_07-click-Lannion_Plestin1.webp](../assets/IMP_07-click-Lannion_Plestin1.webp) | Screenshot evidence |
| [assets/IMP_07-click-Lannion_Plestin1.txt](../assets/IMP_07-click-Lannion_Plestin1.txt) | Text/log evidence |
| [assets/IMP_07-try-mosel4.webp](../assets/IMP_07-try-mosel4.webp) | Screenshot evidence |
| [assets/IMP_07-try-mosel4.txt](../assets/IMP_07-try-mosel4.txt) | Text/log evidence |
| [assets/IMP_07-try-jura2.webp](../assets/IMP_07-try-jura2.webp) | Screenshot evidence |
| [assets/IMP_07-try-jura2.txt](../assets/IMP_07-try-jura2.txt) | Text/log evidence |
| [assets/IMP_07-precise-click-attempts.txt](../assets/IMP_07-precise-click-attempts.txt) | Text/log evidence |

## Screenshot Evidence

![assets/IMP_07-click-Lannion_Plestin1.webp](../assets/IMP_07-click-Lannion_Plestin1.webp)
![assets/IMP_07-try-mosel4.webp](../assets/IMP_07-try-mosel4.webp)
![assets/IMP_07-try-jura2.webp](../assets/IMP_07-try-jura2.webp)

## Timings

| Step | Timing |
|---|---:|
| Evidence review | <1 minute |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
