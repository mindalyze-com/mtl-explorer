# Packet: ADM_11

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: ADM_11
- In scope: Close/reopen Admin without losing current panel state.
- Out of scope: Browser refresh or full logout persistence.

## Prerequisites

- Required previous coverage IDs or run packets: ADM_10
- Required app/data state: Helpers panel open with recent command output.
- Required browser context: Desktop browser.

## Allowed Mutations

- Allowed: Close and reopen the Admin workspace.
- Not allowed: Click the freshness reload action.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ADM_11 | Closed Admin via the navigation tool, then reopened it. | Closing/reopening the dialog does not lose state mid-action. | Reopening restored the Admin workspace with the Helpers panel still open and the recent gcexport command output intact. | PASS | `assets/ADM_11-admin-reopened.webp`, `assets/ADM_11-close-reopen.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/ADM_11-admin-reopened.webp | Screenshot after closing and reopening Admin. |
| assets/ADM_11-close-reopen.txt | Recorded state before close, after close, and after reopen. |

## Timings

| Step | Timing |
|---|---:|
| Close/reopen Admin | <1 min |

## Handoff Notes

- Completed: ADM_11 passed.
- Remaining unfinished coverage: SYN_01 through RUN_CLEANUP.
- Blocked or not applicable: None.
- State left for the next packet: Admin/Helpers panel open; freshness banner remains visible and unclicked.
