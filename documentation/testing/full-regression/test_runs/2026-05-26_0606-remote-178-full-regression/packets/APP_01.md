# Packet: APP_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: APP_01
- In scope: Switch between light and dark UI color schemes.
- Out of scope: Map base-map style changes.

## Prerequisites

- Required previous coverage IDs or run packets: SYN_07
- Required app/data state: Signed in; Admin Settings reachable.
- Required browser context: Desktop browser.

## Allowed Mutations

- Allowed: Change local color scheme.
- Not allowed: Change imported data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| APP_01 | Opened Admin -> Settings and switched Light -> Dark -> Light -> Dark. | Whole UI re-themes immediately. | `data-theme` and pressed Settings controls changed immediately for light and dark; visible Admin/Settings colors changed with the selected scheme. | PASS | `assets/APP_01_02-theme-switch.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/APP_01_02-theme-switch.txt | Theme switch state and color evidence. |

## Timings

| Step | Timing |
|---|---:|
| Theme toggle checks | <2 min |

## Handoff Notes

- Completed: APP_01 passed.
- Remaining unfinished coverage: APP_02 through RUN_CLEANUP.
- Blocked or not applicable: None.
- State left for the next packet: Dark mode selected.
