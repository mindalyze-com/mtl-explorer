# Packet: ADM_07

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: ADM_07
- In scope: Admin Freshness panel timestamp, status, and reload/refresh control.
- Out of scope: Other coverage IDs except where noted as shared state/evidence.

## Prerequisites

- Required previous coverage IDs or run packets: ADM_06 terminal; Freshness tile reachable.
- Required app/data state: Current run-state and packet set for this run.
- Required browser context: As specified by the coverage action.

## Allowed Mutations

- Allowed: Open Freshness, use Refresh, capture evidence, and update ADM_07 packet/run-state.
- Not allowed: Collapse this ID into a parent row or skip required direct evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ADM_07 | Opened Admin > Freshness, inspected status timestamps/domains, clicked Refresh, and confirmed status remained visible. | Data freshness shows last-update timestamp and offers reload. | PASS: Freshness showed In sync, Checked timestamp, Latest change timestamp, domain revision cards, Polling healthy, and a Refresh control; after refresh, status still rendered. | PASS | [assets/ADM_07-freshness-before.webp](../assets/ADM_07-freshness-before.webp); [assets/ADM_07-freshness-after.webp](../assets/ADM_07-freshness-after.webp); [assets/ADM_07-freshness.txt](../assets/ADM_07-freshness.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [assets/ADM_07-freshness-before.webp](../assets/ADM_07-freshness-before.webp) | Screenshot evidence |
| [assets/ADM_07-freshness-after.webp](../assets/ADM_07-freshness-after.webp) | Screenshot evidence |
| [assets/ADM_07-freshness.txt](../assets/ADM_07-freshness.txt) | Text/log evidence |

## Screenshot Evidence

![assets/ADM_07-freshness-before.webp](../assets/ADM_07-freshness-before.webp)
![assets/ADM_07-freshness-after.webp](../assets/ADM_07-freshness-after.webp)

## Timings

| Step | Timing |
|---|---:|
| Freshness open and refresh | ~8 seconds |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
