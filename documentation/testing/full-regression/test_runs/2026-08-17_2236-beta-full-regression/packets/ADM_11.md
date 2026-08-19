# Packet: ADM_11

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: ADM_11
- In scope: Close/reopen behavior while retaining current Admin action state.
- Out of scope: Browser reload persistence.

## Prerequisites

- Required previous coverage IDs or run packets: ADM_10.
- Required app/data state: Completed helper action with output still in the UI.
- Required browser context: Desktop Admin Maintenance.

## Allowed Mutations

- Allowed: Close/reopen the Advanced tools disclosure and full Admin sheet.
- Not allowed: Reload the browser or run another helper action.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ADM_11 | Closed/reopened Advanced tools, closed Admin to the map, reopened Admin, and returned to Maintenance. | Closing/reopening the dialog does not lose state mid-action. | The Done state and complete fit-export output survived both disclosure and full-sheet close/reopen paths byte-for-byte; routes remained synchronized. | PASS | [assets/ADM_11-close-reopen.txt](../assets/ADM_11-close-reopen.txt); [assets/ADM_11-reopened-action.jpg](../assets/ADM_11-reopened-action.jpg) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/ADM_11-close-reopen.txt](../assets/ADM_11-close-reopen.txt) | Before/after routes, disclosure state, and exact output-preservation checks. |
| [assets/ADM_11-reopened-action.jpg](../assets/ADM_11-reopened-action.jpg) | Reopened Maintenance panel with preserved Done/output state. |

## Screenshot Evidence

- The clipped panel preserves the reopened Advanced tools detail, Ready states,
  Done pill, and retained command output.

## Timings

| Step | Timing |
|---|---:|
| Disclosure close/reopen | Under 1 s |
| Admin close/reopen and return | About 2 s |

## Handoff Notes

- Completed: Admin action state survived detail and full-sheet close/reopen.
- Remaining unfinished coverage: None for ADM_11.
- Blocked or not applicable: None.
- State left for the next packet: Authenticated browser is on Admin Maintenance;
  a freshness banner is visible because helper settings changed.
