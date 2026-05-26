# Packet: SYN_06

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: SYN_06
- In scope: Logout/login behavior after applying data freshness reload.
- Out of scope: Wipe-and-logout behavior.

## Prerequisites

- Required previous coverage IDs or run packets: SYN_05
- Required app/data state: Outstanding SYN_05 upload was reloaded before logout.
- Required browser context: Desktop browser.

## Allowed Mutations

- Allowed: Use safe logout and login with README credentials.
- Not allowed: Use `Wipe & Logout`.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SYN_06 | Reloaded current data, logged out with safe Logout, signed back in, and waited. | Logging out and back in does not re-trigger an automatic data refresh repeatedly. | App returned to map with `12 / 12 Tracks`; no freshness banner appeared immediately after login or after a 10 second wait. | PASS | `assets/SYN_06-logout-login.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/SYN_06-logout-login.txt | Logout/login sequence and after-login freshness observations. |

## Timings

| Step | Timing |
|---|---:|
| Reload, logout, login, wait | <2 min |

## Handoff Notes

- Completed: SYN_06 passed.
- Remaining unfinished coverage: SYN_07 through RUN_CLEANUP.
- Blocked or not applicable: None.
- State left for the next packet: Signed in on map with 12 visible tracks and no freshness banner.
