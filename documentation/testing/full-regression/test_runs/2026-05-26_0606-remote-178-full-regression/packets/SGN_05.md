# Packet: SGN_05

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: SGN_05
- In scope: user-facing logout and subsequent login.
- Out of scope: wipe-and-logout destructive local-data path.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_02.
- Required app/data state: app running; user can sign in.
- Required browser context: desktop browser context.

## Allowed Mutations

- Allowed: sign in, open Admin Session controls, use credentials-only Logout, sign in again.
- Not allowed: use Wipe & Logout or alter server data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_05 | Signed in, opened Admin → Session, clicked `Logout`, then signed in again. | Logout returns to login; signing in again works. | `Logout` returned to `/mtl/login`; signing in again reached `/mtl/` with 10 tracks visible. | PASS | `assets/SGN_05-admin-session-scan.webp`, `assets/SGN_05-logout-login.webp`, `assets/SGN_05-relogin-map.webp`, `assets/SGN_05-logout-relogin.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/SGN_05-signout-scan.txt | Initial scan showing logout was not top-level. |
| assets/SGN_05-admin-session-scan.txt | Admin Session tab exposing logout controls. |
| assets/SGN_05-admin-session-scan.webp | Admin Session logout controls. |
| assets/SGN_05-logout-login.webp | Login screen after logout. |
| assets/SGN_05-relogin-map.webp | Map after signing in again. |
| assets/SGN_05-logout-relogin.txt | URL/text/timing summary. |

## Timings

| Step | Timing |
|---|---:|
| Re-login to ready map | 3.6s |

## Handoff Notes

- Completed: SGN_05 terminal PASS.
- Remaining unfinished coverage: continue with SGN_06.
- Blocked or not applicable: none.
- State left for the next packet: no server data changed.
