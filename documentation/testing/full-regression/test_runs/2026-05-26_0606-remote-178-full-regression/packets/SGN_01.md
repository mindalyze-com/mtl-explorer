# Packet: SGN_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: SGN_01
- In scope: opening the app from a signed-out browser context.
- Out of scope: valid/invalid credential submission.

## Prerequisites

- Required previous coverage IDs or run packets: RUN_SETUP.
- Required app/data state: installed app running at `http://178.105.173.254:18080/mtl/`.
- Required browser context: fresh browser context with no MTL Explorer auth session.

## Allowed Mutations

- Allowed: open app URL in a clean browser context.
- Not allowed: modify server state.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_01 | Opened `/mtl/` in a fresh unsigned browser context. | App redirects to the login screen. | Browser reached `http://178.105.173.254:18080/mtl/login` and showed the sign-in screen. | PASS | `assets/SGN_01-login-redirect.webp`, `assets/SGN_01-login-redirect.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/SGN_01-login-redirect.webp | Login screen after opening while signed out. |
| assets/SGN_01-login-redirect.txt | URL, timing, and visible text summary. |

## Timings

| Step | Timing |
|---|---:|
| Signed-out redirect | 1.2s |

## Handoff Notes

- Completed: SGN_01 terminal PASS.
- Remaining unfinished coverage: continue with SGN_02.
- Blocked or not applicable: none.
- State left for the next packet: no server data changed.
