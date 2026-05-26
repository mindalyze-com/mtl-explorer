# Packet: APP_04

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: APP_04
- In scope: Theme persistence across reload and login.
- Out of scope: Wipe-and-logout behavior.

## Prerequisites

- Required previous coverage IDs or run packets: APP_03
- Required app/data state: Dark mode selected.
- Required browser context: Desktop browser.

## Allowed Mutations

- Allowed: Hard reload, safe logout, login.
- Not allowed: Wipe local app data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| APP_04 | Reloaded in dark mode, logged out safely, and signed back in. | Selected theme persists across reload and login. | `data-theme` remained `dark` after reload, on the login page, and after signing back in. | PASS | `assets/APP_04_05-theme-persistence.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/APP_04_05-theme-persistence.txt | Reload/login theme persistence observations. |

## Timings

| Step | Timing |
|---|---:|
| Reload, logout, login | <3 min |

## Handoff Notes

- Completed: APP_04 passed.
- Remaining unfinished coverage: APP_05 through RUN_CLEANUP.
- Blocked or not applicable: None.
- State left for the next packet: Signed in, dark mode selected.
