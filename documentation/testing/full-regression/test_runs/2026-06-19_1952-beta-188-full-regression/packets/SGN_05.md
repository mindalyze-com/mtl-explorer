# Packet: SGN_05

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: SGN_05
- In scope: Sign out and verify return to login; then sign in again successfully.
- Out of scope: Invalid credentials and startup failure.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_04.
- Required app/data state: browser can sign in with README credentials.
- Required browser context: desktop browser.

## Allowed Mutations

- Allowed: Sign in, sign out with visible Session Logout, and sign in again.
- Not allowed: Wipe all local app data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_05 | Signed in, opened Admin > Session, clicked Logout, verified login screen, then signed in again. | Sign out returns to login; signing in again works. | Logout returned to login with Username/Password/Sign In. Re-login returned to `/mtl/` map with `11 Tracks`. | PASS | [assets/SGN_05-after-logout.webp](../assets/SGN_05-after-logout.webp); [assets/SGN_05-relogin-map.webp](../assets/SGN_05-relogin-map.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SGN_05-after-logout.webp](../assets/SGN_05-after-logout.webp) | Login screen after visible logout. |
| [assets/SGN_05-relogin-map.webp](../assets/SGN_05-relogin-map.webp) | Successful re-login to map. |

## Screenshot Evidence

![After logout](../assets/SGN_05-after-logout.webp)

![Re-login map](../assets/SGN_05-relogin-map.webp)

## Timings

| Step | Timing |
|---|---:|
| Login, logout, and re-login | <1 min |

## Handoff Notes

- Completed: SGN_05.
- Remaining unfinished coverage: SGN_06 onward.
- Blocked or not applicable: none.
- State left for the next packet: Browser is signed in at `/mtl/`.
