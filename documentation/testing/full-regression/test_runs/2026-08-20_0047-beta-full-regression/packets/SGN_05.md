# Packet: SGN_05

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: SGN_05
- In scope: Sign out to login and verify a subsequent valid sign-in.
- Out of scope: Wiping cached data.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_04.
- Required app/data state: Server healthy; valid documented credentials available without storing them in artifacts.
- Required browser context: Signed-out login page in the in-app browser.

## Allowed Mutations

- Allowed: Valid sign-in, credentials-only sign-out, and valid sign-in again.
- Not allowed: Wipe local application data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_05 | Sign in, use Admin > Session > Sign out, then sign in again. | Sign out returns to login; signing in again works. | Sign out returned to `/mtl/login` with login controls; the second valid sign-in returned to `/mtl/` with the nine-track map and normal navigation. | PASS | [assets/SGN_05-signout-relogin.txt](../assets/SGN_05-signout-relogin.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SGN_05-signout-relogin.txt](../assets/SGN_05-signout-relogin.txt) | Visible sign-out and two successful valid-login states without credential values. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible URLs, controls, and loaded map state are recorded in linked evidence.

## Timings

| Step | Timing |
|---|---:|
| Initial valid sign-in | 1.914 s |
| Sign out to login | 0.802 s |
| Sign in again to loaded map | 2.061 s |

## Handoff Notes

- Completed: Credentials-only sign out and successful subsequent sign-in.
- Remaining unfinished coverage: None for SGN_05.
- Blocked or not applicable: None.
- State left for the next packet: Browser signed in at the loaded map.
